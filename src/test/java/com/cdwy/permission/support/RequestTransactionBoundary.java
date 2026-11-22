package com.cdwy.permission.support;

import jakarta.persistence.EntityManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import javax.sql.DataSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * 在回滚的测试事务里模拟“一个 HTTP 请求一个事务”。
 *
 * <p>测试类标注 {@code @Transactional}，整个测试方法在一个事务内执行并在结束时回滚，MockMvc 请求也加入这个事务。
 * 为了让多请求的验收流程与生产行为一致，每个请求：
 *
 * <ul>
 *   <li>开始前把测试代码直接做的改动写入数据库、清空持久化上下文，再设一个保存点；
 *   <li>正常结束时写入并清空持久化上下文，相当于提交后下一个请求重新读库；
 *   <li>以异常结束（由异常处理器翻译成错误响应）时回滚到保存点，相当于该请求的事务回滚。
 * </ul>
 */
@TestConfiguration(proxyBeanMethods = false)
public class RequestTransactionBoundary {

  /** 给 MockMvc 加上请求级事务边界过滤器。 */
  @Bean
  MockMvcBuilderCustomizer requestTransactionBoundaryCustomizer(
      EntityManager entityManager, DataSource dataSource) {
    return builder -> builder.addFilters(new BoundaryFilter(entityManager, dataSource));
  }

  /** 用 JDBC 保存点包住一次请求；不在测试事务内时直接放行。 */
  static final class BoundaryFilter extends OncePerRequestFilter {
    private final EntityManager entityManager;
    private final DataSource dataSource;

    BoundaryFilter(EntityManager entityManager, DataSource dataSource) {
      this.entityManager = entityManager;
      this.dataSource = dataSource;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
      if (!TransactionSynchronizationManager.isActualTransactionActive()) {
        chain.doFilter(request, response);
        return;
      }
      entityManager.flush();
      entityManager.clear();
      Connection connection = DataSourceUtils.getConnection(dataSource);
      Savepoint savepoint = savepoint(connection);
      boolean failed = true;
      try {
        chain.doFilter(request, response);
        failed = request.getAttribute(DispatcherServlet.EXCEPTION_ATTRIBUTE) != null;
        if (!failed) entityManager.flush();
      } finally {
        entityManager.clear();
        finish(connection, savepoint, failed);
        DataSourceUtils.releaseConnection(connection, dataSource);
      }
    }

    private static Savepoint savepoint(Connection connection) {
      try {
        return connection.setSavepoint();
      } catch (SQLException exception) {
        throw new IllegalStateException("无法设置保存点", exception);
      }
    }

    /** 请求失败回滚到保存点，成功则释放保存点。 */
    private static void finish(Connection connection, Savepoint savepoint, boolean failed) {
      try {
        if (failed) connection.rollback(savepoint);
        else connection.releaseSavepoint(savepoint);
      } catch (SQLException exception) {
        throw new IllegalStateException("无法结束保存点", exception);
      }
    }
  }
}
