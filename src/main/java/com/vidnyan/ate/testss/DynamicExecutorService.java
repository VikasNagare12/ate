package com.vidnyan.ate.testss;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

@Service
public class DynamicExecutorService<T> {
    
    public void executeSql(String sql) {
         JdbcTemplate jdbcTemplate  = new JdbcTemplate((DataSource) new Object());
        jdbcTemplate.update(sql); // The evaluator will see "sql" here, not the value.
    }

    public void executeSql(String sql, Object[] args) {
        JdbcTemplate jdbcTemplate  = new JdbcTemplate((DataSource) new Object());
        jdbcTemplate.update(sql); // The evaluator will see "sql" here, not the value.
    }

    public void executeSql(String sql, T bean) {
        JdbcTemplate jdbcTemplate  = new JdbcTemplate((DataSource) new Object());
        jdbcTemplate.update(sql); // The evaluator will see "sql" here, not the value.
    }
}
