/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.example.android.architecture.blueprints.todoapp.data.source.network

class FakeNetworkDataSource(
    var tasks: MutableList<NetworkTask>? = mutableListOf()
) : NetworkDataSource {
    override suspend fun loadTasks() = tasks ?: throw Exception("Task list is null")

    override suspend fun saveTasks(tasks: List<NetworkTask>) {
        this.tasks = tasks.toMutableList()
    }
}
