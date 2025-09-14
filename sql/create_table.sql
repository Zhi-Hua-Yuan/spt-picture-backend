use spt_picture;

-- 创建用户表
create table if not exists user
(
    id           bigint primary key auto_increment comment '用户id' primary key,
    userName     varchar(256)                                                       null comment '用户名称',
    userAccount  varchar(256)                                                       not null comment '用户账号',
    userPassword varchar(512)                                                       not null comment '用户密码',
    userAvatar   varchar(1024)                                                      null comment '用户头像',
    userProfile  varchar(512)                                                       null comment '用户简介',
    userRole     varchar(256) default 'user'                                        not null comment '用户角色：user/admin',
    editTime     timestamp    default current_timestamp                             not null comment '编辑时间',
    createTime   timestamp    default current_timestamp                             not null comment '创建时间',
    updateTime    timestamp    default current_timestamp on update current_timestamp not null comment '更新时间',
    isDelete     tinyint      default 0                                             not null comment '是否删除 0-不删除 1-删除',
    unique key uk_userAccount (userAccount),
    index idx_userName (userName)
) comment '用户表' collate = utf8mb4_unicode_ci;