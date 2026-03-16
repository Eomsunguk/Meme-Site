update meme_category
set active = false
where category_key in ('programming', 'dailylife', 'animals');

update meme_source_config
set active = false
where category_id in (
    select id from meme_category where category_key in ('programming', 'dailylife', 'animals')
);

update meme_source_config
set query_value = 'ProgrammerHumor'
where category_id = (select id from meme_category where category_key = 'work')
  and source_type = 'REDDIT'
  and display_order = 2;

update meme_source_config
set query_value = 'kpopmemes'
where category_id = (select id from meme_category where category_key = 'kpop')
  and source_type = 'REDDIT'
  and display_order = 2;

update meme_source_config
set query_value = 'nbacirclejerk'
where category_id = (select id from meme_category where category_key = 'sports')
  and source_type = 'REDDIT'
  and display_order = 2;

insert into meme_batch (run_date, started_at, ended_at, status, message, item_count) values
    ('2026-02-01', timestamp '2026-02-01 09:00:00', timestamp '2026-02-01 09:00:03', 'SUCCESS', 'Seeded February archive batch', 4),
    ('2026-03-01', timestamp '2026-03-01 09:00:00', timestamp '2026-03-01 09:00:03', 'SUCCESS', 'Seeded March archive batch', 4);

insert into meme_snapshot (batch_id, category_id, title, media_type, media_url, source_url, summary, tags, source, popularity, rank_order)
select batch.id, category.id, seeded.title, 'image', seeded.media_url, seeded.source_url, seeded.summary, seeded.tags, 'Archive Seed', seeded.popularity, 1
from (
    select '2026-02-01' as run_date, 'gaming' as category_key, 'Patch notes dropped and the meta exploded overnight' as title,
           '/img/archive/gaming-2026-02.svg' as media_url, 'https://www.reddit.com/r/gamingmemes/' as source_url,
           'February archive seed for the gaming lane.' as summary, 'gaming, patch, ranked' as tags, 982 as popularity
    union all
    select '2026-02-01', 'work', 'Daily standup somehow became a feature request tribunal',
           '/img/archive/work-2026-02.svg', 'https://www.reddit.com/r/workmemes/',
           'February archive seed for work memes.', 'work, standup, office', 811
    union all
    select '2026-02-01', 'kpop', 'Comeback teaser hit and the group chat forgot how to sleep',
           '/img/archive/kpop-2026-02.svg', 'https://www.reddit.com/r/kpoopheads/',
           'February archive seed for K-pop fandom jokes.', 'kpop, comeback, fandom', 877
    union all
    select '2026-02-01', 'sports', 'Coach said trust the process right before another overtime loss',
           '/img/archive/sports-2026-02.svg', 'https://www.reddit.com/r/sportsmemes/',
           'February archive seed for sports reactions.', 'sports, overtime, coach', 903
    union all
    select '2026-03-01', 'gaming', 'Squad said one quick match and now the sun is coming up',
           '/img/archive/gaming-2026-03.svg', 'https://www.reddit.com/r/gamingmemes/',
           'March archive seed for the gaming lane.', 'gaming, squad, late-night', 1042
    union all
    select '2026-03-01', 'work', 'When the sprint board says just one quick fix',
           '/img/archive/work-2026-03.svg', 'https://www.reddit.com/r/ProgrammerHumor/',
           'March archive seed for work memes.', 'work, office, sprint', 864
    union all
    select '2026-03-01', 'kpop', 'Bias changed again after one fancam',
           '/img/archive/kpop-2026-03.svg', 'https://www.reddit.com/r/kpoopheads/',
           'March archive seed for K-pop fandom jokes.', 'kpop, fandom, fancam', 955
    union all
    select '2026-03-01', 'sports', 'Team rebuilding for the fifth year in a row',
           '/img/archive/sports-2026-03.svg', 'https://www.reddit.com/r/sportsmemes/',
           'March archive seed for sports reactions.', 'sports, rebuild, reaction', 918
) seeded
join meme_batch batch on batch.run_date = cast(seeded.run_date as date) and batch.message like 'Seeded%'
join meme_category category on category.category_key = seeded.category_key;
