/**
 * 菜单树节点
 */
export interface MenuTreeNode {
  id: number
  parentId: number
  permCode: string
  permName: string
  permType: 'MENU' | 'BUTTON' | 'API' | string
  path?: string
  component?: string
  icon?: string
  sortOrder?: number
  visible?: number
  children?: MenuTreeNode[]
}
