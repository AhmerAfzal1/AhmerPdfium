package com.ahmer.pdfium.treeview

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView


class TreeViewAdapter<VH : RecyclerView.ViewHolder?>(
    nodes: List<TreeViewNode<*>>?,
    viewBinders: List<TreeViewBinderInterface<*>>
) :
    RecyclerView.Adapter<VH>(), View.OnClickListener {
    private val viewBinders: List<TreeViewBinderInterface<*>>
    protected val displayNodes: ArrayList<TreeViewNode<*>>? = ArrayList()
    protected var padding = 30
    protected var onTreeNodeListener: OnTreeNodeListener? = null
    private var toCollapseChild = false
    protected var lastSelectionOffset = 0
    protected var lastSelectionId: Long = 0
    protected var lastSelectionPos = 0
    protected var findSelection = false
    protected var currentFilter: Any? = null
    var currentSchFlg = 0
    protected var rootNode: TreeViewNode<*>? = null
    protected var footNode: TreeViewNode<*>? = null
    var isFolderView = false
        protected set
    var currentExpChannel = 0x1
    protected var normalExpChannel = 0x1
    protected var schViewExpChannel = 0x1

    constructor(viewBinders: List<TreeViewBinderInterface<*>>) : this(null, viewBinders) {}

    protected fun addChildNodesFiltered(
        pNode: TreeViewNode<*>,
        startIndex: Int,
        schFlag: Int
    ): Int {
        if (currentFilter == null) {
            return addChildNodes(pNode, startIndex)
        }
        var addChildCount = 0
        val nodes = pNode.getChildList(isFolderView)
        val schView = schFlag and 0x2 != 0
        val shldAdd = schFlag and 0x1 != 0
        val schViewExp = if (schView) schViewExpChannel else normalExpChannel
        var node: TreeViewNode<*>
        var i = 0
        val len = nodes!!.size
        while (i < len) {
            node = nodes[i]
            val added = filterNode(node)
            val expanded = node.isExpand(schViewExp)
            val currentIdx = startIndex + addChildCount++
            if (shldAdd) {
                displayNodes!!.add(currentIdx, node)
            }
            var flagNxt = schFlag
            if (shldAdd && !expanded) {
                flagNxt = flagNxt and 0x1.inv()
            }
            val childAdd = addChildNodesFiltered(node, currentIdx + 1, flagNxt)
            if (childAdd > 0) {
                if (shldAdd && expanded) {
                    addChildCount += childAdd
                }
            } else if (!added) {
                addChildCount--
                if (shldAdd) {
                    displayNodes!!.removeAt(currentIdx)
                }
            }
            i++
        }
        return addChildCount
    }

    interface TreeTraveller<T : TreeViewNode<*>?> {
        fun onNodeReached(node: T): Boolean
        fun ended(): Boolean
    }

    fun <T : TreeViewNode<*>?> TraverseChildTree(pNode: T, treeTraveller: TreeTraveller<T>) {
        val nodes: List<T> = pNode!!.getChildList(isFolderView) as List<T>
        var node: T
        var i = 0
        var len = nodes.size
        while (i < len) {
            node = nodes[i]
            val removed = treeTraveller.onNodeReached(node)
            if (treeTraveller.ended()) {
                return
            }
            if (removed) {
                --len
                --i
            } else if (!node!!.isLeaf) {
                TraverseChildTree(node, treeTraveller)
            }
            i++
        }
    }

    protected fun filterDisplayNodes(nodes: List<TreeViewNode<*>>?, schFlag: Int): Boolean {
        var ret = false
        val schView = schFlag and 0x2 != 0
        val shldAdd = schFlag and 0x1 != 0
        val schViewExp = if (schView) schViewExpChannel else normalExpChannel
        var currentIdx: Int
        var flagNxt: Int
        var node: TreeViewNode<*>
        if (nodes != null) { //sanity check
            var i = 0
            val len = nodes.size
            while (i < len) {
                node = nodes[i]
                val added = filterNode(node)
                currentIdx = displayNodes!!.size
                if (shldAdd) {
                    displayNodes.add(node)
                }
                flagNxt = schFlag
                if (schView) {
                    //todo add bit
                    node.expand(schViewExp)
                } else if (!node.isExpand(schViewExp)) {
                    flagNxt = flagNxt and 0x1.inv()
                }
                if (filterDisplayNodes(node.getChildList(), flagNxt) || added) {
                    ret = true
                } else if (shldAdd) {
                    displayNodes.removeAt(currentIdx)
                }
                i++
            }
        }
        return ret
    }

    protected fun filterNode(nodes: TreeViewNode<*>?): Boolean {
        return false
    }

    @JvmOverloads
    fun filterTreeView(pattern: Any? = currentFilter, schFlag: Int = currentSchFlg) {
        if (rootNode != null) {
            currentSchFlg = schFlag
            displayNodes!!.clear()
            if (pattern != null) {
                currentFilter = pattern
                filterDisplayNodes(rootNode!!.getChildList(isFolderView), schFlag or 0x1)
            } else {
                currentFilter = null
                findDisplayNodes(rootNode!!.getChildList(isFolderView))
            }
            if (footNode != null) {
                displayNodes.add(footNode!!)
            }
            notifyDataSetChanged()
        }
    }

    /**
     * 从nodes的结点中寻找展开了的非叶结点，添加到displayNodes中。
     *
     * @param nodes 基准点
     */
    private fun findDisplayNodes(nodes: List<TreeViewNode<*>>?) {
        if (nodes != null) { //sanity check
            var i = 0
            val len = nodes.size
            while (i < len) {
                val node = nodes[i]
                if (findSelection && lastSelectionId == System.identityHashCode(node.content)
                        .toLong()
                ) {
                    lastSelectionPos = displayNodes!!.size
                    findSelection = false
                }
                displayNodes!!.add(node)
                if (node.isExpand(currentExpChannel)) findDisplayNodes(
                    node.getChildList(
                        isFolderView
                    )
                )
                i++
            }
        }
    }

    private fun findSetDisplayNodes(nodes: List<TreeViewNode<*>>?, `val`: Boolean) {
        if (nodes != null) { //sanity check
            var i = 0
            val len = nodes.size
            while (i < len) {
                val node = nodes[i]
                node.setExpanded(currentExpChannel, `val`)
                if (findSelection && lastSelectionId == System.identityHashCode(node.content)
                        .toLong()
                ) {
                    lastSelectionPos = displayNodes!!.size
                    findSelection = false
                }
                displayNodes!!.add(node)
                if (`val`) findSetDisplayNodes(node.getChildList(isFolderView), `val`)
                i++
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return (displayNodes!![position].content as LayoutItemType).layoutId
    }

    override fun getItemId(position: Int): Long {
        return System.identityHashCode(displayNodes!![position].content).toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
        var ret: VH? = null
        for (viewBinder in viewBinders) {
            if (viewBinder.layoutId == viewType) {
                ret = viewBinder.provideViewHolder(v) as VH?
                break
            }
        }
        assert(ret != null)
        ret!!.itemView.setOnClickListener(this)
        return ret
    }

    override fun onBindViewHolder(holder: VH, position: Int, payloads: List<Any>) {
        if (payloads.isNotEmpty()) { // 存疑
            val b = payloads[0] as Bundle
            for (key in b.keySet()) {
                if (KEY_IS_EXPAND == key) {
                    if (onTreeNodeListener != null) onTreeNodeListener!!.onToggle(
                        b.getBoolean(key),
                        holder
                    )
                }
            }
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val nodeI = displayNodes!![position]
        holder!!.itemView.setPaddingRelative(nodeI.height * padding, 3, 3, 3)
        for (viewBinder in viewBinders) {
            if (viewBinder.layoutId == (nodeI.content as LayoutItemType).layoutId) {
                viewBinder.bindView(holder, position, nodeI)
                break
            }
        }
    }

    private fun addChildNodes(pNode: TreeViewNode<*>, startIndex: Int): Int {
        val childList = pNode.getChildList(isFolderView)
        var addChildCount = 0
        for (treeViewNode in childList!!) {
            displayNodes!!.add(startIndex + addChildCount++, treeViewNode)
            if (treeViewNode.isExpand(currentExpChannel)) {
                addChildCount += addChildNodes(treeViewNode, startIndex + addChildCount)
            }
        }
        if (!pNode.isExpand(currentExpChannel)) pNode.toggle(currentExpChannel)
        return addChildCount
    }

    //todo check
    private fun removeChildNodes(pNode: TreeViewNode<*>): Int {
        if (pNode.isLeaf) return 0
        val childList = pNode.getChildList()
        var removeChildCount = childList!!.size
        displayNodes!!.removeAll(childList)
        for (child in childList) {
            if (child.isExpand(currentExpChannel)) {
                if (toCollapseChild) child.toggle(currentExpChannel)
                removeChildCount += removeChildNodes(child)
            }
        }
        return removeChildCount
    }

    private fun removeChildNodesFast(pNode: TreeViewNode<*>): Int {
        return removeChildNodesFast(displayNodes!!.indexOf(pNode))
    }

    private fun removeChildNodesFast(position: Int): Int {
        var ret = 0
        if (position >= 0 && position < displayNodes!!.size) {
            val pNode = displayNodes[position]
            pNode.collapse(currentExpChannel)
            var i = position + 1
            var len: Int = displayNodes.size
            while (i < len) {
                val node = displayNodes[i]
                if (isChildOf(node, pNode)) {
                    displayNodes.removeAt(i)
                    --len
                    --i
                    ++ret
                } else {
                    break
                }
                i++
            }
        }
        return ret
    }

    private fun isChildOf(node: TreeViewNode<*>, pNode: TreeViewNode<*>): Boolean {
        var mNode = node
        while (mNode.parent.also { mNode = it!! } != null) {
            if (mNode == pNode) {
                return true
            }
        }
        return false
    }

    override fun getItemCount(): Int {
        return if (displayNodes == null) 0 else displayNodes.size
    }

    @JvmName("setPadding1")
    fun setPadding(padding: Int) {
        this.padding = padding
    }

    fun ifCollapseChildWhileCollapseParent(toCollapseChild: Boolean) {
        this.toCollapseChild = toCollapseChild
    }

    fun setTreeNodeListener(onTreeNodeListener: OnTreeNodeListener?) {
        this.onTreeNodeListener = onTreeNodeListener
    }

    override fun onClick(view: View) {
        val holder = view.tag as VH
        val nodeI = displayNodes!![holder!!.layoutPosition]
        //Log.e("fatal", "getLayoutPosition "+holder.getLayoutPosition()+"  "+nodeI.toString());
        if (onTreeNodeListener != null && onTreeNodeListener!!.onClick(nodeI, holder)
            || nodeI.isLeaf /* || nodeI.isLocked()*/) {
            return
        }
        val positionStart = holder.layoutPosition + 1
        nodeI.toggle(currentExpChannel)
        if (nodeI.isExpand(currentExpChannel)) {
            notifyItemRangeInserted(
                positionStart, addChildNodesFiltered(nodeI, positionStart, 0x1)
            )
        } else {
            notifyItemRangeRemoved(positionStart, removeChildNodes(nodeI))
        }
    }

    interface OnTreeNodeListener {
        /**
         * called when TreeNodes were clicked.
         * @return weather consume the click event.
         */
        fun onClick(node: TreeViewNode<*>?, holder: RecyclerView.ViewHolder?): Boolean

        /**
         * called when TreeNodes were toggle.
         * @param isExpand the status of TreeNodes after being toggled.
         */
        fun onToggle(isExpand: Boolean, holder: RecyclerView.ViewHolder?)
    }

    fun refresh(treeViewNodes: List<TreeViewNode<*>?>?, cap: Int) {
        displayNodes!!.clear()
        displayNodes.ensureCapacity(cap)
        filterTreeView()
        notifyDataSetChanged()
    }

    fun refresh(
        view: RecyclerView?,
        treeViewNodes: List<TreeViewNode<*>>?,
        cap: Int,
        `val`: Boolean
    ) {
        var llm: LinearLayoutManager? = null
        if (view != null) {
            llm = view.layoutManager as LinearLayoutManager?
            lastSelectionId = getItemId(llm!!.findFirstVisibleItemPosition())
            val ca = view.getChildAt(0)
            if (ca != null) {
                lastSelectionOffset = ca.top
            }
        }
        findSelection = true
        displayNodes!!.clear()
        displayNodes.ensureCapacity(cap)
        findSetDisplayNodes(treeViewNodes, `val`)
        notifyDataSetChanged()
        if (llm != null && !findSelection) {
            llm.scrollToPositionWithOffset(lastSelectionPos, lastSelectionOffset)
        }
    }

    val displayNodesIterator: Iterator<TreeViewNode<*>>
        get() = displayNodes!!.iterator()

    private fun notifyDiff(temp: List<TreeViewNode<*>>) {
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int {
                return temp.size
            }

            override fun getNewListSize(): Int {
                return displayNodes!!.size
            }

            // judge if the same items
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return this@TreeViewAdapter.areItemsTheSame(
                    temp[oldItemPosition],
                    displayNodes!![newItemPosition]
                )
            }

            // if they are the same items, whether the contents has bean changed.
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return this@TreeViewAdapter.areContentsTheSame(
                    temp[oldItemPosition],
                    displayNodes!![newItemPosition]
                )
            }

            override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? {
                return this@TreeViewAdapter.getChangePayload(
                    temp[oldItemPosition],
                    displayNodes!![newItemPosition]
                )
            }
        })
        diffResult.dispatchUpdatesTo(this)
    }

    private fun getChangePayload(oldNode: TreeViewNode<*>, newNode: TreeViewNode<*>): Any? {
        val diffBundle = Bundle()
        if (newNode.isExpand(currentExpChannel) != oldNode.isExpand(currentExpChannel)) {
            diffBundle.putBoolean(KEY_IS_EXPAND, newNode.isExpand(currentExpChannel))
        }
        return if (diffBundle.size() == 0) null else diffBundle
    }

    // For DiffUtil, if they are the same items, whether the contents has bean changed.
    private fun areContentsTheSame(oldNode: TreeViewNode<*>, newNode: TreeViewNode<*>): Boolean {
        return oldNode.content != null && oldNode.content == newNode.content && oldNode.isExpand(
            currentExpChannel
        ) == newNode.isExpand(currentExpChannel)
    }

    // judge if the same item for DiffUtil
    private fun areItemsTheSame(oldNode: TreeViewNode<*>, newNode: TreeViewNode<*>): Boolean {
        return oldNode.content != null && oldNode.content == newNode.content
    }

    /**
     * collapse all root nodes.
     */
    fun collapseAll() {
//        // Back up the nodes are displaying.
//        List<TreeViewNode> temp = backupDisplayNodes();
//        //find all root nodes.
//        List<TreeViewNode> roots = new ArrayList<>();
//        for (TreeViewNode displayNode : displayNodes) {
//            if (displayNode.isRoot())
//                roots.add(displayNode);
//        }
//        //Close all root nodes.
//        for (TreeViewNode root : roots) {
//            if (root.isExpand(currentExpChannel)) {
//				root.collapse(currentExpChannel); removeChildNodes(root);
//			}
//
//        }
//        notifyDiff(temp);
        rootNode!!.collapseAll(currentExpChannel)
        refreshList(true)
    }

    fun expandAll() {
        rootNode!!.expandAll(currentExpChannel)
        refreshList(true)
    }

    fun collapseExpandToLevel(collapse: Boolean, level: Int) {
        if (collapse) {
            rootNode!!.collapseLevel(currentExpChannel, level)
        } else {
            rootNode!!.expandLevel(currentExpChannel, level)
        }
        refreshList(true)
    }

    protected fun refreshList(findCurrentPos: Boolean) {
        refresh(rootNode!!.getChildList(isFolderView), 0)
    }

    private fun backupDisplayNodes(): List<TreeViewNode<*>> {
        val temp: MutableList<TreeViewNode<*>> = ArrayList()
        for (displayNode in displayNodes!!) {
            try {
                temp.add(displayNode.clone())
            } catch (e: CloneNotSupportedException) {
                temp.add(displayNode)
            }
        }
        return temp
    }

    fun collapseNode(pNode: TreeViewNode<*>?) {
//        List<TreeViewNode> temp = backupDisplayNodes();
//		pNode.collapse(currentExpChannel); removeChildNodes(pNode);
//        notifyDiff(temp);
    }

    fun collapseNode(position: Int) {
//        List<TreeViewNode> temp = backupDisplayNodes();
//		pNode.collapse(currentExpChannel); removeChildNodes(pNode);
//        notifyDiff(temp);
        val removed = removeChildNodesFast(position)
        if (removed > 0) {
            notifyItemRangeRemoved(position + 1, removed)
        }
    }

    fun collapseBrotherNode(pNode: TreeViewNode<*>) {
        val temp = backupDisplayNodes()
        if (pNode.isRoot) {
            val roots: MutableList<TreeViewNode<*>> = ArrayList()
            for (displayNode in displayNodes!!) {
                if (displayNode.isRoot) roots.add(displayNode)
            }
            //Close all root nodes.
            for (root in roots) {
                if (root.isExpand(currentExpChannel) && root != pNode) {
                    root.collapse(currentExpChannel)
                    removeChildNodes(root)
                }
            }
        } else {
            val parent = pNode.parent ?: return
            val childList = parent.getChildList()
            for (node in childList!!) {
                if (node == pNode || !node.isExpand(currentExpChannel)) continue
                node.collapse(currentExpChannel)
                removeChildNodes(node)
            }
        }
        notifyDiff(temp)
    }

    //	@Override
    //	public void unregisterAdapterDataObserver(@NonNull RecyclerView.AdapterDataObserver observer) {
    //		super.unregisterAdapterDataObserver(observer);
    //		Log.e("fatal adapter", "unregisterAdapterDataObserver");
    //	}
    //
    //	@Override
    //	public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
    //		super.onDetachedFromRecyclerView(recyclerView);
    //		Log.e("fatal adapter", "onDetachedFromRecyclerView");
    //	}
    interface LayoutItemType {
        val layoutId: Int
    }

    abstract class TreeViewBinderInterface<VH> : LayoutItemType {
        abstract fun provideViewHolder(itemView: View?): VH
        abstract fun bindView(
            holder: RecyclerView.ViewHolder?,
            position: Int,
            node: TreeViewNode<*>?
        )
    }

    companion object {
        private const val KEY_IS_EXPAND = "IS_EXPAND"
    }

    init {
        findDisplayNodes(nodes)
        this.viewBinders = viewBinders
    }
}