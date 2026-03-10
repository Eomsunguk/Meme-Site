create table meme_category (
    id bigint not null auto_increment primary key,
    category_key varchar(64) not null,
    name varchar(120) not null,
    description varchar(255) not null,
    display_order integer not null,
    active boolean not null default true,
    constraint uk_meme_category_key unique (category_key)
);

create table meme_source_config (
    id bigint not null auto_increment primary key,
    category_id bigint not null,
    source_type varchar(32) not null,
    query_value varchar(255) not null,
    fetch_limit integer not null,
    region_code varchar(16),
    display_order integer not null,
    active boolean not null default true,
    constraint fk_meme_source_config_category foreign key (category_id) references meme_category (id)
);

create table meme_batch (
    id bigint not null auto_increment primary key,
    run_date date not null,
    started_at timestamp(6) not null,
    ended_at timestamp(6),
    status varchar(32) not null,
    message varchar(500),
    item_count integer not null default 0
);

create table meme_snapshot (
    id bigint not null auto_increment primary key,
    batch_id bigint not null,
    category_id bigint not null,
    title varchar(255) not null,
    media_type varchar(32) not null,
    media_url varchar(1000) not null,
    source_url varchar(1000) not null,
    summary varchar(500) not null,
    tags varchar(255) not null,
    source varchar(64) not null,
    popularity bigint not null,
    rank_order integer not null,
    constraint fk_meme_snapshot_batch foreign key (batch_id) references meme_batch (id),
    constraint fk_meme_snapshot_category foreign key (category_id) references meme_category (id)
);

create index idx_meme_batch_status_run_date on meme_batch (status, run_date, started_at);
create index idx_meme_snapshot_batch_rank on meme_snapshot (batch_id, category_id, rank_order);
create index idx_meme_source_config_category on meme_source_config (category_id, source_type, active);

insert into meme_category (category_key, name, description, display_order, active) values
    ('gaming', 'Gaming', 'Trending gaming memes', 1, true),
    ('work', 'Work', 'Office and work-life meme trends', 2, true),
    ('kpop', 'K-POP', 'K-pop fandom meme trends', 3, true),
    ('sports', 'Sports', 'Sports reaction meme trends', 4, true);

insert into meme_source_config (category_id, source_type, query_value, fetch_limit, region_code, display_order, active)
select id, 'REDDIT', 'gamingmemes', 20, null, 1, true from meme_category where category_key = 'gaming';
insert into meme_source_config (category_id, source_type, query_value, fetch_limit, region_code, display_order, active)
select id, 'REDDIT', 'gaming', 20, null, 2, true from meme_category where category_key = 'gaming';
insert into meme_source_config (category_id, source_type, query_value, fetch_limit, region_code, display_order, active)
select id, 'YOUTUBE', 'gaming meme', 5, 'KR', 3, true from meme_category where category_key = 'gaming';
insert into meme_source_config (category_id, source_type, query_value, fetch_limit, region_code, display_order, active)
select id, 'X', '(gaming meme) has:media -is:retweet', 8, null, 4, true from meme_category where category_key = 'gaming';

insert into meme_source_config (category_id, source_type, query_value, fetch_limit, region_code, display_order, active)
select id, 'REDDIT', 'workmemes', 20, null, 1, true from meme_category where category_key = 'work';
insert into meme_source_config (category_id, source_type, query_value, fetch_limit, region_code, display_order, active)
select id, 'REDDIT', 'ProgrammerHumor', 20, null, 2, true from meme_category where category_key = 'work';
insert into meme_source_config (category_id, source_type, query_value, fetch_limit, region_code, display_order, active)
select id, 'YOUTUBE', 'office meme', 5, 'KR', 3, true from meme_category where category_key = 'work';
insert into meme_source_config (category_id, source_type, query_value, fetch_limit, region_code, display_order, active)
select id, 'X', '(office meme OR monday meme) has:media -is:retweet', 8, null, 4, true from meme_category where category_key = 'work';

insert into meme_source_config (category_id, source_type, query_value, fetch_limit, region_code, display_order, active)
select id, 'REDDIT', 'kpoopheads', 20, null, 1, true from meme_category where category_key = 'kpop';
insert into meme_source_config (category_id, source_type, query_value, fetch_limit, region_code, display_order, active)
select id, 'REDDIT', 'kpopthoughts', 20, null, 2, true from meme_category where category_key = 'kpop';
insert into meme_source_config (category_id, source_type, query_value, fetch_limit, region_code, display_order, active)
select id, 'YOUTUBE', 'kpop meme', 5, 'KR', 3, true from meme_category where category_key = 'kpop';
insert into meme_source_config (category_id, source_type, query_value, fetch_limit, region_code, display_order, active)
select id, 'X', '(kpop meme) has:media -is:retweet', 8, null, 4, true from meme_category where category_key = 'kpop';

insert into meme_source_config (category_id, source_type, query_value, fetch_limit, region_code, display_order, active)
select id, 'REDDIT', 'sportsmemes', 20, null, 1, true from meme_category where category_key = 'sports';
insert into meme_source_config (category_id, source_type, query_value, fetch_limit, region_code, display_order, active)
select id, 'REDDIT', 'soccer', 20, null, 2, true from meme_category where category_key = 'sports';
insert into meme_source_config (category_id, source_type, query_value, fetch_limit, region_code, display_order, active)
select id, 'YOUTUBE', 'sports meme', 5, 'KR', 3, true from meme_category where category_key = 'sports';
insert into meme_source_config (category_id, source_type, query_value, fetch_limit, region_code, display_order, active)
select id, 'X', '(sports meme OR football meme) has:media -is:retweet', 8, null, 4, true from meme_category where category_key = 'sports';
