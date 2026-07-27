/**
 * 将字符串的首字母大写
 * @param string
 */
function capitalizeFirstLetter(string: string): string {
  return string.charAt(0).toUpperCase() + string.slice(1);
}

/**
 * 将字符串的首字母转换为小写。
 *
 * @param str 要转换的字符串
 * @returns 首字母小写的字符串
 */
function toLowerCaseFirstLetter(str: string): string {
  if (!str) return str; // 如果字符串为空，直接返回
  return str.charAt(0).toLowerCase() + str.slice(1);
}

/**
 * 将子键名与父键名拼接为驼峰格式
 * @description 将 parentKey 与首字母大写的 key 拼接，如 (userName, home) => userNameHome
 * @param key 子键名
 * @param parentKey 父键名
 * @returns 拼接后的键名
 */
function concatKeyWithParent(key: string, parentKey: string): string {
  if (!parentKey) {
    return key;
  }
  return parentKey + key.charAt(0).toUpperCase() + key.slice(1);
}

function kebabToCamelCase(str: string): string {
  return str
    .split('-')
    .filter(Boolean)
    .map((word, index) =>
      index === 0 ? word : word.charAt(0).toUpperCase() + word.slice(1),
    )
    .join('');
}

export {
  capitalizeFirstLetter,
  concatKeyWithParent,
  kebabToCamelCase,
  toLowerCaseFirstLetter,
};
