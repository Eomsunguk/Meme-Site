delete from meme_snapshot;
delete from meme_batch;

update meme_source_config
set active = false;

insert into meme_source_config (category_id, source_type, query_value, fetch_limit, region_code, display_order, active)
select id, 'IMGFLIP', 'offset:0', 10, null, 1, true
from meme_category
where category_key = 'gaming'
  and not exists (
      select 1 from meme_source_config
      where category_id = meme_category.id
        and source_type = 'IMGFLIP'
  );

insert into meme_source_config (category_id, source_type, query_value, fetch_limit, region_code, display_order, active)
select id, 'IMGFLIP', 'offset:10', 10, null, 1, true
from meme_category
where category_key = 'work'
  and not exists (
      select 1 from meme_source_config
      where category_id = meme_category.id
        and source_type = 'IMGFLIP'
  );

insert into meme_source_config (category_id, source_type, query_value, fetch_limit, region_code, display_order, active)
select id, 'IMGFLIP', 'offset:20', 10, null, 1, true
from meme_category
where category_key = 'kpop'
  and not exists (
      select 1 from meme_source_config
      where category_id = meme_category.id
        and source_type = 'IMGFLIP'
  );

insert into meme_source_config (category_id, source_type, query_value, fetch_limit, region_code, display_order, active)
select id, 'IMGFLIP', 'offset:30', 10, null, 1, true
from meme_category
where category_key = 'sports'
  and not exists (
      select 1 from meme_source_config
      where category_id = meme_category.id
        and source_type = 'IMGFLIP'
  );

update meme_source_config
set active = true
where source_type = 'IMGFLIP';
