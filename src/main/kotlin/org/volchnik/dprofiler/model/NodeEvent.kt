package org.volchnik.dprofiler.model

data class NodeEvent(val node: DiskNode, val child: DiskNode, val type: EventType)