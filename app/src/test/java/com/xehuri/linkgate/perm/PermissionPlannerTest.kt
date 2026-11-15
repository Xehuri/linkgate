package com.xehuri.linkgate.perm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionPlannerTest {

    @Test
    fun `所有版本都包含麦克风摄像头和定位`() {
        listOf(26, 28, 30, 33, 34, 36).forEach { sdk ->
            val permissions = PermissionPlanner.items(sdk).map { it.permission }
            assertTrue("sdk $sdk 缺麦克风", PermNames.RECORD_AUDIO in permissions)
            assertTrue("sdk $sdk 缺摄像头", PermNames.CAMERA in permissions)
            assertTrue("sdk $sdk 缺精确定位", PermNames.ACCESS_FINE_LOCATION in permissions)
            assertTrue("sdk $sdk 缺粗略定位", PermNames.ACCESS_COARSE_LOCATION in permissions)
        }
    }

    @Test
    fun `Android 8 需要存储读写权限但没有媒体细分权限`() {
        val permissions = PermissionPlanner.items(26).map { it.permission }
        assertTrue(PermNames.READ_EXTERNAL_STORAGE in permissions)
        assertTrue(PermNames.WRITE_EXTERNAL_STORAGE in permissions)
        assertFalse(PermNames.POST_NOTIFICATIONS in permissions)
        assertFalse(PermNames.READ_MEDIA_IMAGES in permissions)
    }

    @Test
    fun `Android 10 到 12 只要读存储不要写存储`() {
        listOf(29, 30, 31, 32).forEach { sdk ->
            val permissions = PermissionPlanner.items(sdk).map { it.permission }
            assertTrue("sdk $sdk 应要读存储", PermNames.READ_EXTERNAL_STORAGE in permissions)
            assertFalse("sdk $sdk 不该要写存储", PermNames.WRITE_EXTERNAL_STORAGE in permissions)
        }
    }

    @Test
    fun `Android 13 起改用媒体权限与通知权限`() {
        listOf(33, 34, 36).forEach { sdk ->
            val permissions = PermissionPlanner.items(sdk).map { it.permission }
            assertTrue(PermNames.POST_NOTIFICATIONS in permissions)
            assertTrue(PermNames.READ_MEDIA_IMAGES in permissions)
            assertTrue(PermNames.READ_MEDIA_VIDEO in permissions)
            assertTrue(PermNames.READ_MEDIA_AUDIO in permissions)
            assertFalse(PermNames.READ_EXTERNAL_STORAGE in permissions)
            assertFalse(PermNames.WRITE_EXTERNAL_STORAGE in permissions)
        }
    }

    @Test
    fun `同一版本内权限不重复`() {
        listOf(26, 33).forEach { sdk ->
            val permissions = PermissionPlanner.items(sdk).map { it.permission }
            assertEquals(permissions.size, permissions.distinct().size)
        }
    }

    @Test
    fun `pending 过滤已授予项并保持顺序`() {
        val all = PermissionPlanner.items(33).map { it.permission }
        val granted = setOf(all[0], all[3])
        val pending = PermissionPlanner.pending(33, granted)
        assertEquals(all.size - 2, pending.size)
        assertFalse(all[0] in pending)
        assertFalse(all[3] in pending)
        assertEquals(all.filter { it !in granted }, pending)
    }

    @Test
    fun `全部授予时 pending 为空`() {
        val all = PermissionPlanner.items(33).map { it.permission }.toSet()
        assertTrue(PermissionPlanner.pending(33, all).isEmpty())
    }

    @Test
    fun `签名与顺序无关且可用于去重`() {
        val a = listOf("p1", "p2", "p3")
        val b = listOf("p3", "p2", "p1")
        assertEquals(PermissionPlanner.signature(a), PermissionPlanner.signature(b))
    }
}
