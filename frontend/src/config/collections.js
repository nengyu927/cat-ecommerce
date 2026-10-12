export const collections = [
  { slug: 'quiet-home', eyebrow: 'THE SLOW LIVING EDIT', title: '把日常，過成喜歡的樣子。', shortTitle: '一起慢下來的家', description: '從一張柔軟的床、一只溫潤的碗開始，替主子留一個剛剛好的生活角落。', image: '/campaigns/cat-lifestyle-hero.jpg', categories: ['beds', 'dining', 'scratchers'], tone: 'oat', tag: '居家生活提案', details: ['柔軟的休息角落', '融入居家的材質', '每天都用得上的選品'] },
  { slug: 'daily-table', eyebrow: 'A WELL-SET TABLE', title: '每一餐，都值得好好準備。', shortTitle: '主子的餐桌日常', description: '主食、補水與好用的食碗，讓每天的用餐時間成為值得期待的小事。', image: '/product-images/ceramic-bowl.jpg', categories: ['food', 'dining'], tone: 'sage', tag: '餐桌選品', details: ['主食與補水一起安排', '食碗與水碗分開放置', '依食量挑選合適規格'] },
  { slug: 'afternoon-nap', eyebrow: 'SOFT HOURS', title: '把午後，留給一場好眠。', shortTitle: '午後舒眠計畫', description: '柔軟布料、自然色調和安心的環抱感。把窗邊最舒服的位置，留給主子。', image: '/product-images/linen-cat-bed.jpg', categories: ['beds'], tone: 'sand', tag: '舒眠選品', details: ['依睡姿挑選大小', '選擇方便整理的內墊', '放在熟悉而安靜的位置'] },
  { slug: 'play-together', eyebrow: 'LITTLE MOMENTS OF PLAY', title: '陪玩，是每天的小約定。', shortTitle: '一起玩的十分鐘', description: '追逐、躲藏、伸懶腰。讓好奇心有地方安放，也替每天留一點一起玩的時間。', image: '/product-images/cat-scratcher.jpg', categories: ['toys', 'scratchers'], tone: 'clay', tag: '互動與抓磨', details: ['互動玩具交替使用', '抓磨與休息兼顧', '收好玩具再享用點心'] },
  { slug: 'gentle-care', eyebrow: 'CARE, WITH A GENTLE TOUCH', title: '溫柔照顧，從小事開始。', shortTitle: '清爽日常整理', description: '梳毛、清潔與換砂，一次整理好日常工具。用舒服的節奏，陪主子完成每天的照顧。', image: '/product-images/cat-grooming.jpg', categories: ['grooming', 'litter', 'health'], tone: 'sage', tag: '日常照顧', details: ['短時間溫柔梳理', '保持生活環境乾爽', '工具用後清潔收納'] },
  { slug: 'little-journey', eyebrow: 'READY FOR A LITTLE JOURNEY', title: '一起出門，也要安心自在。', shortTitle: '城市裡的小旅行', description: '從熟悉提籠開始，慢慢準備外出的每一步。帶上熟悉的氣味，和剛剛好的安心感。', image: '/product-images/cat-carrier.jpg', categories: ['travel', 'beds'], tone: 'sand', tag: '外出準備', details: ['先在家熟悉提籠', '依體型確認內部空間', '帶上熟悉的休息墊'] }
]
export function flattenCategories(nodes) { return nodes.flatMap(node => [node, ...flattenCategories(node.children || [])]) }
export function categoryDescendants(categories, slugs) {
  const ids = new Set(categories.filter(category => slugs.includes(category.slug)).map(category => String(category.id)))
  let changed = true
  while (changed) { changed = false; for (const category of categories) if (ids.has(String(category.parentId)) && !ids.has(String(category.id))) { ids.add(String(category.id)); changed = true } }
  return ids
}
