import random
from pathlib import Path

surnames = ['王', '李', '张', '刘', '陈', '杨', '黄', '赵', '吴', '周', '徐', '孙', '马', '朱', '胡', '郭', '何', '高', '林', '罗', '郑', '梁', '谢', '宋', '唐', '许', '韩', '冯', '邓', '曹', '彭', '曾', '肖', '田', '董', '袁', '潘', '于', '蒋', '蔡', '余', '杜', '叶', '程', '苏', '魏', '吕', '丁', '任', '沈']
male_names = ['伟', '强', '磊', '洋', '勇', '军', '杰', '涛', '明', '刚', '平', '辉', '超', '浩', '波', '鹏', '飞', '鑫', '斌', '宇', '顺', '伦', '越', '博', '凯', '航', '铭', '轩', '睿', '晨', '昊', '天', '泽', '毅', '峰', '建', '宏', '达', '成', '东']
female_names = ['芳', '娜', '敏', '静', '丽', '娟', '艳', '霞', '秀', '燕', '萍', '玲', '丹', '红', '玉', '兰', '洁', '梅', '琳', '素', '云', '莲', '真', '环', '雪', '荣', '爱', '妹', '香', '月', '莺', '媛', '瑞', '婷', '欣', '雅', '倩', '颖', '萱', '菲']

core_students = [
    ('20246085', '周宇斌', '男'),
    ('20246074', '郭振顺', '男'),
    ('20245727', '王嘉伦', '男'),
    ('20245796', '沈越', '男'),
    ('STU2026001', '陈晨', '男'),
    ('20246001', '李明', '男'),
    ('20246002', '张华', '女'),
    ('20246003', '赵雪', '女'),
    ('20246004', '孙强', '男'),
]

students = list(core_students)
used_ids = set(s[0] for s in students)
used_names = set(s[1] for s in students)

random.seed(2026)
curr_id = 20246005
while len(students) < 95:
    sid = str(curr_id)
    curr_id += 1
    if sid in used_ids:
        continue
    gender = '女' if random.random() < 0.3 else '男'
    surname = random.choice(surnames)
    first = random.choice(female_names if gender == '女' else male_names)
    if random.random() < 0.4:
        first += random.choice(female_names if gender == '女' else male_names)
    name = surname + first
    if name in used_names:
        continue
    used_names.add(name)
    used_ids.add(sid)
    students.append((sid, name, gender))

print('Total generated:', len(students))
sql = ['USE classroom_ai;', 'TRUNCATE TABLE student;', 'INSERT INTO student (student_id, name, gender, class_name, avatar_url, created_at, updated_at) VALUES']
vals = []
for sid, name, gender in students:
    avatar = f'https://api.dicebear.com/7.x/bottts/svg?seed={sid}'
    vals.append(f"('{sid}', '{name}', '{gender}', '软件工程2024级2班', '{avatar}', NOW(), NOW())")
sql.append(',\n'.join(vals) + ';')

output_path = Path(__file__).resolve().parent / 'init_95_students.sql'
with output_path.open('w', encoding='utf-8') as f:
    f.write('\n'.join(sql))
print(f'Success written to {output_path}')
