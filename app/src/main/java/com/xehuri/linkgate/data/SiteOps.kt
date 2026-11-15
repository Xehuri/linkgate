package com.xehuri.linkgate.data

/**
 * 站点列表的纯函数操作：增删改与排序。全部返回新列表，方便在 Compose 里配合状态使用，
 * 也方便单元测试覆盖「置顶/上移」的边界情况。
 */
object SiteOps {

    fun add(sites: List<Site>, site: Site): List<Site> = sites + site

    fun update(sites: List<Site>, site: Site): List<Site> =
        sites.map { if (it.id == site.id) site else it }

    fun remove(sites: List<Site>, id: String): List<Site> =
        sites.filterNot { it.id == id }

    /** 置顶：移到列表最前面；已在最前或不存在时原样返回。 */
    fun moveToTop(sites: List<Site>, id: String): List<Site> {
        val index = sites.indexOfFirst { it.id == id }
        if (index <= 0) return sites
        val target = sites[index]
        return listOf(target) + sites.filterIndexed { i, _ -> i != index }
    }

    /** 上移一格；已在最前或不存在时原样返回。 */
    fun moveUp(sites: List<Site>, id: String): List<Site> {
        val index = sites.indexOfFirst { it.id == id }
        if (index <= 0) return sites
        return sites.toMutableList().apply {
            val previous = this[index - 1]
            this[index - 1] = this[index]
            this[index] = previous
        }
    }

    /** 下移一格；已在最后或不存在时原样返回。 */
    fun moveDown(sites: List<Site>, id: String): List<Site> {
        val index = sites.indexOfFirst { it.id == id }
        if (index < 0 || index >= sites.size - 1) return sites
        return sites.toMutableList().apply {
            val next = this[index + 1]
            this[index + 1] = this[index]
            this[index] = next
        }
    }
}
