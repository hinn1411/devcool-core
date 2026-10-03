-- Baseline: the schema the JPA entities mapped before Flyway took over (P1-T01).
-- Generated from Hibernate's schema script, then reviewed by hand: lowercase unquoted names,
-- readable constraint names matching the entities, no redundant UNIQUE on primary keys.
-- Ids stay INTEGER here; the BIGINT move is P3-T01, as its own migration.

-- Sequences: one per entity table, named <table>_seq. INCREMENT BY 50 matches JPA's default
-- allocationSize (Hibernate's pooled optimizer)
create sequence app_user_seq       start with 1 increment by 50;
create sequence auth_provider_seq  start with 1 increment by 50;
create sequence channel_seq        start with 1 increment by 50;
create sequence friend_request_seq start with 1 increment by 50;
create sequence member_seq         start with 1 increment by 50;
create sequence message_seq        start with 1 increment by 50;
create sequence media_seq          start with 1 increment by 50;
create sequence topic_seq          start with 1 increment by 50;

-- Tables
create table app_user (
    id              integer      not null,
    user_name       varchar(20),
    password        varchar(255),
    email           varchar(50),
    email_verified  boolean,
    name            varchar(50),
    avatar          varchar(255),
    role            varchar(255) not null,
    status          varchar(255) not null,
    last_login_time timestamp(6) with time zone,
    token_version   integer,
    constraint pk_app_user primary key (id),
    constraint uk_email unique (email),
    constraint uk_username unique (user_name),
    constraint ck_app_user_role check (role in ('USER', 'ADMIN')),
    constraint ck_app_user_status check (status in ('ACTIVE', 'DELETED', 'BLOCKED'))
);

create table auth_provider (
    id           integer      not null,
    user_id      integer      not null,
    provider     varchar(255) not null,
    provider_id  varchar(255) not null,
    created_time timestamp(6) not null,
    constraint pk_auth_provider primary key (id),
    constraint ck_auth_provider_provider check (provider in ('BASIC', 'GITHUB'))
);

create table channel (
    id               integer      not null,
    name             varchar(255) not null,
    boundary         varchar(255) not null,
    total_of_members integer      not null,
    expired_time     timestamp(6) with time zone,
    channel_type     varchar(255) not null,
    creator_id       integer,
    leader_id        integer,
    constraint pk_channel primary key (id),
    constraint ck_channel_boundary check (boundary in ('PUBLIC', 'PRIVATE')),
    constraint ck_channel_channel_type check (channel_type in ('LOUNGE', 'FORUM', 'PRIVATE_CHAT'))
);

create table friend_request (
    id             integer      not null,
    request_id     varchar(255) not null,
    created_time   timestamp(6) not null,
    processed_time timestamp(6) not null,
    sender_id      integer      not null,
    receiver_id    integer      not null,
    constraint pk_friend_request primary key (id),
    constraint uk_friend_request_request_id unique (request_id)
);

create table member (
    id          integer      not null,
    user_id     integer      not null,
    channel_id  integer      not null,
    role        varchar(255) not null,
    joined_time timestamp(6) with time zone not null,
    constraint pk_member primary key (id),
    constraint uk_member_channel_user unique (user_id, channel_id),
    constraint ck_member_role check (role in ('MEMBER', 'CREATOR', 'LEADER'))
);

create table message (
    id           integer      not null,
    user_id      integer      not null,
    channel_id   integer      not null,
    content      varchar(1000),
    content_type varchar(255) not null,
    created_time timestamp(6) with time zone not null,
    deleted_time timestamp(6) with time zone,
    edited_time  timestamp(6) with time zone,
    constraint pk_message primary key (id),
    constraint ck_message_content_type
        check (content_type in ('TEXT', 'MARKDOWN', 'IMAGE', 'VIDEO'))
);

create table media (
    id           integer      not null,
    path         varchar(500) not null,
    created_time timestamp(6) with time zone not null,
    message_id   integer,
    constraint pk_media primary key (id),
    constraint uk_media_message unique (message_id)
);

-- refresh_token.user_id is VARCHAR with no FK to app_user, as mapped today; out of scope for V1.
create table refresh_token (
    jti           varchar(255) not null,
    user_id       varchar(255) not null,
    issued_time   timestamp(6) with time zone,
    expired_time  timestamp(6) with time zone,
    consumed_time timestamp(6) with time zone,
    constraint pk_refresh_token primary key (jti)
);

create table topic (
    id   integer      not null,
    name varchar(255) not null,
    constraint pk_topic primary key (id)
);

-- No primary key, as mapped today.
create table topics_of_channels (
    topic_id   integer not null,
    channel_id integer not null
);

-- Foreign keys
alter table auth_provider
    add constraint fk_auth_provider_user foreign key (user_id) references app_user (id);
alter table channel
    add constraint fk_channel_creator foreign key (creator_id) references app_user (id);
alter table channel
    add constraint fk_channel_leader foreign key (leader_id) references app_user (id);
alter table friend_request
    add constraint fk_friend_request_sender foreign key (sender_id) references app_user (id);
alter table friend_request
    add constraint fk_friend_request_receiver foreign key (receiver_id) references app_user (id);
alter table member
    add constraint fk_member_user foreign key (user_id) references app_user (id);
alter table member
    add constraint fk_member_channel foreign key (channel_id) references channel (id);
alter table message
    add constraint fk_message_user foreign key (user_id) references app_user (id);
alter table message
    add constraint fk_message_channel foreign key (channel_id) references channel (id);
alter table media
    add constraint fk_media_message foreign key (message_id) references message (id);
alter table topics_of_channels
    add constraint fk_topics_of_channels_topic foreign key (topic_id) references topic (id);
alter table topics_of_channels
    add constraint fk_topics_of_channels_channel foreign key (channel_id) references channel (id);
