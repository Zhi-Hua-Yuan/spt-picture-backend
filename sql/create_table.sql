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
    updateTime   timestamp    default current_timestamp on update current_timestamp not null comment '更新时间',
    isDelete     tinyint      default 0                                             not null comment '是否删除 0-不删除 1-删除',
    unique key uk_userAccount (userAccount),
    index idx_userName (userName)
) comment '用户表' collate = utf8mb4_unicode_ci;

-- 图片表
create table if not exists picture
(
    id           bigint auto_increment comment 'id' primary key,
    url          varchar(512)                       not null comment '图片 url',
    name         varchar(128)                       not null comment '图片名称',
    introduction varchar(512)                       null comment '简介',
    category     varchar(64)                        null comment '分类',
    tags         varchar(512)                       null comment '标签（JSON 数组）',
    picSize      bigint                             null comment '图片体积',
    picWidth     int                                null comment '图片宽度',
    picHeight    int                                null comment '图片高度',
    picScale     double                             null comment '图片宽高比例',
    picFormat    varchar(32)                        null comment '图片格式',
    userId       bigint                             not null comment '创建用户 id',
    createTime   datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    editTime     datetime default CURRENT_TIMESTAMP not null comment '编辑时间',
    updateTime   datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint  default 0                 not null comment '是否删除',
    INDEX idx_name (name),                 -- 提升基于图片名称的查询性能
    INDEX idx_introduction (introduction), -- 用于模糊搜索图片简介
    INDEX idx_category (category),         -- 提升基于分类的查询性能
    INDEX idx_tags (tags),                 -- 提升基于标签的查询性能
    INDEX idx_userId (userId)              -- 提升基于用户 ID 的查询性能
) comment '图片' collate = utf8mb4_unicode_ci;

-- 图片表增加审核功能相关列表
alter table picture
    add column reviewStatus  int          not null default 0 comment '审核状态：0-待审核 1-审核通过 2-审核未通过',
    add column reviewMessage varchar(512) null comment '审核信息',
    add column reviewerId    bigint       not null comment '审核人 id',
    add column reviewTime    datetime     null comment '审核时间';

-- 创建基于 reviewStatus 列的索引
create index idx_reviewStatus on picture (reviewStatus);