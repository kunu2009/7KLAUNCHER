package com.sevenk.launcher.data

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class TodoViewModel(private val dao: TodoDao) : ViewModel() {

    val allTodos: LiveData<List<TodoItem>> = dao.getAll().asLiveData()

    fun insert(item: TodoItem) = viewModelScope.launch {
        dao.insert(item)
    }

    fun update(item: TodoItem) = viewModelScope.launch {
        dao.update(item)
    }

    fun delete(item: TodoItem) = viewModelScope.launch {
        dao.delete(item)
    }
}

class TodoViewModelFactory(private val dao: TodoDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TodoViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TodoViewModel(dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
