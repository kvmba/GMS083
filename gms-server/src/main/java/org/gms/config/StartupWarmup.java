/*
    This file is part of the BeiDou MapleStory Server
 */
package org.gms.config;

import lombok.extern.slf4j.Slf4j;
import org.gms.util.DatabaseConnection;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 启动预热:应用就绪后立即创建一条数据库连接,
 * 避免首次登录时现场创建物理连接带来的冷启动尖峰(此前出现过 16 秒的登录耗时)。
 */
@Slf4j
@Component
public class StartupWarmup {

    @EventListener(ApplicationReadyEvent.class)
    public void warmUpDatabaseConnection() {
        try (Connection con = DatabaseConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT 1")) {
            if (rs.next()) {
                log.info("启动预热:数据库连接就绪");
            }
        } catch (SQLException e) {
            log.warn("启动预热:数据库连接失败({})", e.getMessage());
        }
    }
}
