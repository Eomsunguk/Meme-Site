-- Insert initial meme categories
INSERT INTO meme_category (category_key, name, description, display_order, active) VALUES
('programming', 'Programming Memes', 'Memes about programming, coding, and developer life', 1, true),
('dailylife', 'Daily Life', 'Everyday humor and relatable life situations', 2, true),
('animals', 'Animal Memes', 'Funny memes featuring animals', 3, true);

-- Insert initial meme source configurations (Reddit only, since API keys are not set)
INSERT INTO meme_source_config (category_id, source_type, query_value, fetch_limit, region_code, display_order, active) VALUES
((SELECT id FROM meme_category WHERE category_key = 'programming'), 'REDDIT', 'ProgrammerHumor', 10, NULL, 1, true),
((SELECT id FROM meme_category WHERE category_key = 'dailylife'), 'REDDIT', 'funny', 10, NULL, 1, true),
((SELECT id FROM meme_category WHERE category_key = 'animals'), 'REDDIT', 'aww', 10, NULL, 1, true);