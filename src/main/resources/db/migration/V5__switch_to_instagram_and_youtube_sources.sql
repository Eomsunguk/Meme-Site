update meme_source_config
set active = false
where source_type in ('REDDIT', 'X');

insert into meme_source_config (category_id, source_type, query_value, fetch_limit, region_code, display_order, active)
select id, 'INSTAGRAM', 'gamingmeme', 5, null, 1, true
from meme_category
where category_key = 'gaming'
  and not exists (
      select 1 from meme_source_config
      where category_id = meme_category.id
        and source_type = 'INSTAGRAM'
  );

insert into meme_source_config (category_id, source_type, query_value, fetch_limit, region_code, display_order, active)
select id, 'INSTAGRAM', 'workmemes', 5, null, 1, true
from meme_category
where category_key = 'work'
  and not exists (
      select 1 from meme_source_config
      where category_id = meme_category.id
        and source_type = 'INSTAGRAM'
  );

insert into meme_source_config (category_id, source_type, query_value, fetch_limit, region_code, display_order, active)
select id, 'INSTAGRAM', 'kpopmeme', 5, null, 1, true
from meme_category
where category_key = 'kpop'
  and not exists (
      select 1 from meme_source_config
      where category_id = meme_category.id
        and source_type = 'INSTAGRAM'
  );

insert into meme_source_config (category_id, source_type, query_value, fetch_limit, region_code, display_order, active)
select id, 'INSTAGRAM', 'sportsmeme', 5, null, 1, true
from meme_category
where category_key = 'sports'
  and not exists (
      select 1 from meme_source_config
      where category_id = meme_category.id
        and source_type = 'INSTAGRAM'
  );
