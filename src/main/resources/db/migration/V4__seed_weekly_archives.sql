insert into meme_batch (run_date, started_at, ended_at, status, message, item_count) values
    ('2026-03-03', timestamp '2026-03-03 09:00:00', timestamp '2026-03-03 09:00:03', 'SUCCESS', 'Seeded weekly archive batch for two weeks ago', 40),
    ('2026-03-10', timestamp '2026-03-10 09:00:00', timestamp '2026-03-10 09:00:03', 'SUCCESS', 'Seeded weekly archive batch for last week', 40);

insert into meme_snapshot (batch_id, category_id, title, media_type, media_url, source_url, summary, tags, source, popularity, rank_order)
select
    batch.id,
    category.id,
    concat(
        case
            when batch.run_date = cast('2026-03-10' as date) then '지난주'
            else '지지난주'
        end,
        ' ',
        case category.category_key
            when 'gaming' then 'gaming'
            when 'work' then 'work'
            when 'kpop' then 'kpop'
            else 'sports'
        end,
        ' meme #',
        ranks.rank_order
    ) as title,
    'image' as media_type,
    concat('/img/archive/', category.category_key, '-2026-03.svg') as media_url,
    case category.category_key
        when 'gaming' then 'https://www.reddit.com/r/gamingmemes/'
        when 'work' then 'https://www.reddit.com/r/ProgrammerHumor/'
        when 'kpop' then 'https://www.reddit.com/r/kpoopheads/'
        else 'https://www.reddit.com/r/sportsmemes/'
    end as source_url,
    concat(
        case
            when batch.run_date = cast('2026-03-10' as date) then '지난주'
            else '지지난주'
        end,
        ' ',
        category.name,
        ' archive seed item ',
        ranks.rank_order
    ) as summary,
    concat(category.category_key, ', archive, week-', ranks.rank_order) as tags,
    'Archive Seed' as source,
    1200 - (ranks.rank_order * 17)
        + case category.category_key
            when 'gaming' then 40
            when 'work' then 30
            when 'kpop' then 20
            else 10
          end
        + case
            when batch.run_date = cast('2026-03-10' as date) then 60
            else 0
          end as popularity,
    ranks.rank_order
from meme_batch batch
join meme_category category on category.category_key in ('gaming', 'work', 'kpop', 'sports')
join (
    select 1 as rank_order
    union all select 2
    union all select 3
    union all select 4
    union all select 5
    union all select 6
    union all select 7
    union all select 8
    union all select 9
    union all select 10
) ranks on 1 = 1
where batch.run_date in (cast('2026-03-03' as date), cast('2026-03-10' as date));
