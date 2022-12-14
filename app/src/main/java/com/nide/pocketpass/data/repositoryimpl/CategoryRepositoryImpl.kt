package com.nide.pocketpass.data.repositoryimpl

import com.nide.pocketpass.data.local.datasource.CategoryLocalDataSource
import com.nide.pocketpass.data.module.Category
import com.nide.pocketpass.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow

class CategoryRepositoryImpl(private val categoryLocalDataSource: CategoryLocalDataSource) :
    CategoryRepository {
    override fun getAllCategories(): Flow<List<Category>> {
        return categoryLocalDataSource.getCategories()
    }

    override fun insertCategory(category: Category) {
        categoryLocalDataSource.insertCategory(category)
    }


}