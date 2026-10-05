-- ==========================================================================
-- V2__init_dict.sql
-- 初始化字典数据（sys_config）：品类 / 成色 / 搭子类型 / 活动分类 / 竞赛级别
-- 来源：《12-数据库设计文档》§2「品类、成色、专场运营位均配置于此」
--      值与前端 framework/constants.js 严格一致
-- ==========================================================================

INSERT INTO `sys_config` (`cfg_key`, `cfg_value`, `remark`) VALUES
('goods.category', '[{"value":1,"label":"数码"},{"value":2,"label":"书籍"},{"value":3,"label":"生活用品"},{"value":4,"label":"运动装备"},{"value":5,"label":"服饰"},{"value":6,"label":"乐器"},{"value":7,"label":"其他"}]', '二手商品品类'),
('goods.cond', '[{"value":1,"label":"全新"},{"value":2,"label":"几乎全新"},{"value":3,"label":"轻微使用痕迹"},{"value":4,"label":"明显使用痕迹"},{"value":5,"label":"功能完好有瑕疵"}]', '商品成色'),
('buddy.type', '[{"value":1,"label":"学习"},{"value":2,"label":"运动"},{"value":3,"label":"娱乐"},{"value":4,"label":"旅行"},{"value":5,"label":"饭搭子"},{"value":6,"label":"技能"}]', '搭子类型'),
('buddy.feeMode', '[{"value":0,"label":"AA 制"},{"value":1,"label":"请客"}]', '搭子费用方式'),
('activity.category', '[{"value":1,"label":"校园活动"},{"value":2,"label":"学术讲座"},{"value":3,"label":"学术调研"}]', '活动分类'),
('competition.level', '[{"value":1,"label":"校级"},{"value":2,"label":"省级"},{"value":3,"label":"国家级"}]', '竞赛级别'),
('award.result', '[{"value":0,"label":"无"},{"value":1,"label":"一等奖"},{"value":2,"label":"二等奖"},{"value":3,"label":"三等奖"},{"value":4,"label":"优秀奖"},{"value":5,"label":"入围"}]', '竞赛成果'),
('report.reason', '[{"value":1,"label":"虚假信息"},{"value":2,"label":"涉嫌诈骗"},{"value":3,"label":"侵权盗用"},{"value":4,"label":"违规内容"},{"value":5,"label":"其他"}]', '举报原因')
ON DUPLICATE KEY UPDATE `cfg_value` = VALUES(`cfg_value`);
