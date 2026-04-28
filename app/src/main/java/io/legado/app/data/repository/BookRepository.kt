package io.legado.app.data.repository

import io.legado.app.data.appDb

class BookRepository {

    suspend fun getChapterTitle(bookName: String, bookAuthor: String, chapterIndex: Int): String? {
        val book = appDb.bookDao.getBook(bookName, bookAuthor) ?: return null
        val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, chapterIndex) ?: return null
        return chapter.title
    }

    suspend fun getBookCoverByNameAndAuthor(bookName: String, bookAuthor: String): String? {
        val book = appDb.bookDao.getBook(bookName, bookAuthor) ?: return null
        return book.coverUrl
    }

    suspend fun getBookDurChapterTitle(bookName: String, bookAuthor: String): String? {
        val book = appDb.bookDao.getBook(bookName, bookAuthor) ?: return null
        return book.durChapterTitle
    }
}
