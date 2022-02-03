package com.ahmer.pdfium.treeview

import androidx.annotation.NonNull

open class TreeViewNode<T>(
    @param:NonNull val content: T
) : Cloneable {
    var parent: TreeViewNode<*>? = null
    private var childList: ArrayList<TreeViewNode<*>>?
    private var foldListValid = false
    private var foldList: MutableList<TreeViewNode<*>>? = null
    private var isExpand = 0

    //the tree height
    var height = UNDEFINE

    @JvmName("getHeight1")
    fun getHeight(): Int {
        if (isRoot) height = 0 else if (parent != null) height = parent!!.getHeight() + 1
        return height
    }

    val isRoot: Boolean
        get() = parent == null
    val isLeaf: Boolean
        get() = childList == null || childList!!.isEmpty()

    fun getChildList(): List<TreeViewNode<*>>? {
        return childList
    }

    fun getChildList(isFolderView: Boolean): List<TreeViewNode<*>>? {
        if (isFolderView) {
            if (!foldListValid) {
                foldList!!.clear()
                var i = 0
                val len: Int = childList!!.size
                while (i < len) {
                    if (!childList!![i].isLeaf) {
                        foldList!!.add(childList!![i])
                    }
                    i++
                }
                foldListValid = true
            }
            return foldList
        }
        return childList
    }

    fun setChildList(childList: List<TreeViewNode<*>>) {
        this.childList!!.clear()
        for (treeViewNode in childList) {
            addChild(treeViewNode)
        }
    }

    fun addChild(node: TreeViewNode<*>): TreeViewNode<*> {
        if (childList == null) childList = ArrayList()
        childList!!.add(node)
        node.parent = this
        return this
    }

    fun toggle(channelMask: Int) {
        val `val` = isExpand and channelMask == 0
        isExpand = isExpand and channelMask.inv()
        if (`val`) {
            isExpand = isExpand or channelMask
        }
    }

    fun collapse(channelMask: Int) {
        isExpand = isExpand and channelMask.inv()
    }

    fun collapseAll(channelMask: Int) {
        collapse(channelMask)
        if (childList == null || childList!!.isEmpty()) {
            return
        }
        for (child in childList!!) {
            child.collapseAll(channelMask)
        }
    }

    fun collapseLevel(channelMask: Int, level: Int) {
        if (childList == null) {
            return
        }
        val lv = getHeight()
        if (lv == level) {
            collapse(channelMask)
        }
        if (lv < level) {
            for (child in childList!!) {
                child.collapseLevel(channelMask, level)
            }
        }
    }

    fun expand(channelMask: Int) {
        isExpand = isExpand or channelMask
    }

    fun expandAll(channelMask: Int) {
        expand(channelMask)
        if (childList == null || childList!!.isEmpty()) {
            return
        }
        for (child in childList!!) {
            child.expandAll(channelMask)
        }
    }

    fun expandLevel(channelMask: Int, level: Int) {
        if (childList == null) {
            return
        }
        val lv = getHeight()
        if (lv == level) {
            expand(channelMask)
        }
        if (lv < level) {
            for (child in childList!!) {
                child.expandLevel(channelMask, level)
            }
        }
    }

    fun isExpand(): Boolean {
        return isExpand(0x1)
    }

    fun isExpand(channelMask: Int): Boolean {
        return isExpand and channelMask != 0
    }

    @JvmName("setParent1")
    fun setParent(parent: TreeViewNode<*>?) {
        this.parent = parent
    }

    @JvmName("getParent1")
    fun getParent(): TreeViewNode<*>? {
        return parent
    }

    @NonNull
    override fun toString(): String {
        return "TreeNode{" +
                "content=" + content +
                ", parent=" + (if (parent == null) "null" else parent!!.content.toString()) +
                ", childList=" + (if (childList == null) "null" else childList.toString()) +
                ", isExpand=" + isExpand +
                '}'
    }

    @NonNull
    @Throws(CloneNotSupportedException::class)
    public override fun clone(): TreeViewNode<T> {
        val clone = TreeViewNode(content)
        clone.isExpand = isExpand
        return clone
    }

    fun setExpanded(channelMask: Int, `val`: Boolean) {
        isExpand = isExpand and channelMask.inv()
        if (`val`) {
            isExpand = isExpand or channelMask
        }
    }

    val childCount: Int
        get() = childList!!.size

    companion object {
        const val UNDEFINE = -1
    }

    init {
        childList = ArrayList()
    }
}