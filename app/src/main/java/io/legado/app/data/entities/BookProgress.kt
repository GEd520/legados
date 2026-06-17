package io.legado.app.data.entities

data class BookProgress(
    val name: String,
    val author: String,
    val durChapterIndex: Int,
    val durChapterPos: Int,
    val durChapterTime: Long,
    val durChapterTitle: String?
) {

    constructor(book: Book) : this(
        name = book.name,
        author = book.author,
        durChapterIndex = book.durChapterIndex,
        durChapterPos = book.durChapterPos,
        durChapterTime = book.durChapterTime,
        durChapterTitle = book.durChapterTitle
    )

    fun compareWith(book: Book): Int {
        return compareWith(BookProgress(book))
    }

    fun compareWith(progress: BookProgress): Int {
        if (durChapterTime > 0 && progress.durChapterTime > 0 &&
            durChapterTime != progress.durChapterTime
        ) {
            return durChapterTime.compareTo(progress.durChapterTime)
        }
        val chapterCompare = durChapterIndex.compareTo(progress.durChapterIndex)
        if (chapterCompare != 0) {
            return chapterCompare
        }
        return durChapterPos.compareTo(progress.durChapterPos)
    }

    fun isNewerThan(book: Book): Boolean = compareWith(book) > 0

    fun isOlderThan(book: Book): Boolean = compareWith(book) < 0

}
