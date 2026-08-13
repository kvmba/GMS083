package org.gms.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.sql.Date;
import java.sql.Timestamp;

/**
 * 账号列表出参:与 AccountsDO 一致,但剔除敏感字段(password/pin/pic/macs),避免凭据信息泄露给管理端
 */
@Data
public class AccountListDTO implements Serializable {
    private Integer id;
    private String name;
    private Integer loggedin;
    private Timestamp lastlogin;
    private Timestamp createdat;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "Asia/Shanghai")
    private Date birthday;
    private Boolean banned;
    private String banreason;
    private Integer nxCredit;
    private Integer maplePoint;
    private Integer nxPrepaid;
    private Integer characterslots;
    private Integer gender;
    private Timestamp tempban;
    private Integer greason;
    private Boolean tos;
    private String sitelogged;
    private Integer webadmin;
    private String nick;
    private Integer mute;
    private String email;
    private Integer rewardpoints;
    private Integer votepoints;
    private Integer language;
}
