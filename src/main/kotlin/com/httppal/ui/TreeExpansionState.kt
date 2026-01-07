package com.httppal.ui

import javax.swing.JTree
import javax.swing.tree.TreePath

/**
 * 树展开状态
 * 
 * 捕获和恢复树节点的展开状态，用于视图切换时保持用户的展开偏好
 */
data class TreeExpansionState(
    val expandedPaths: Set<String> = emptySet()
) {
    /**
     * 从树中捕获当前的展开状态
     */
    fun captureFrom(tree: JTree): TreeExpansionState {
        val expanded = mutableSetOf<String>()
        
        // 遍历所有行，检查哪些是展开的
        for (i in 0 until tree.rowCount) {
            val path = tree.getPathForRow(i)
            if (path != null && tree.isExpanded(path)) {
                // 使用路径的字符串表示作为键
                expanded.add(pathToString(path))
            }
        }
        
        return TreeExpansionState(expandedPaths = expanded)
    }
    
    /**
     * 将展开状态应用到树
     * 
     * @param tree 目标树
     * @param nodeMap 节点路径映射（字符串键 -> TreePath）
     */
    fun applyTo(tree: JTree, nodeMap: Map<String, TreePath>) {
        expandedPaths.forEach { pathString ->
            nodeMap[pathString]?.let { treePath ->
                tree.expandPath(treePath)
            }
        }
    }
    
    /**
     * 将 TreePath 转换为字符串键
     */
    private fun pathToString(path: TreePath): String {
        return path.path.joinToString("/") { it.toString() }
    }
    
    companion object {
        /**
         * 创建空的展开状态
         */
        fun empty(): TreeExpansionState {
            return TreeExpansionState(emptySet())
        }
    }
}
