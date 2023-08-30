package org.volchnik.dprofiler

fun <T> calcInsertPosition(size: Long, from: Int, to: Int, container: T, getSize: T.(Int) -> Long): Int =
    when (from == to) {
        true -> from
        else -> {
            val mid = from + ((to - from) / 2)
            when (size > container.getSize(mid)) {
                true -> calcInsertPosition(size = size, from = from, to = mid, getSize = getSize, container = container)
                else -> calcInsertPosition(size = size, from = mid + 1, to = to, getSize = getSize, container = container)
            }
        }
    }
