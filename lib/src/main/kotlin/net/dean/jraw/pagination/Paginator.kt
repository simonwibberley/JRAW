package net.dean.jraw.pagination

import net.dean.jraw.RedditClient
import net.dean.jraw.models.Listing
import net.dean.jraw.models.Sorting
import net.dean.jraw.models.Thing
import net.dean.jraw.models.TimePeriod

abstract class Paginator<T : Thing, out B : Paginator.Builder<T>> protected constructor(
    val reddit: RedditClient,
    val baseUrl: String,
    val timePeriod: TimePeriod,
    val sorting: Sorting,
    val limit: Int
) : RedditIterable<T> {
    // Internal, modifiable properties
    private var _current: Listing<T>? = null
    private var _pageNumber = 0

    // Publicly available property is simply an unmodifiable alias to the private properties
    override val current: Listing<T>?
        get() = _current
    override val pageNumber: Int
        get() = _pageNumber

    override fun next(): Listing<T> {
        val args: MutableMap<String, String> = mutableMapOf(
            "limit" to limit.toString(radix = 10)
        )

        if (sorting.requiresTimePeriod)
            args.put("t", timePeriod.name.toLowerCase())

        if (_current != null && _current!!.after != null)
            args.put("after", _current!!.after!!)


        val response = reddit.request {
            it.path("$baseUrl/${sorting.name.toLowerCase()}")
                .query(args)
        }

        _current = response.deserialize()
        _pageNumber++

        return _current!!
    }

    override fun restart() {
        this._current = null
        this._pageNumber = 0
    }

    override fun iterator(): Iterator<Listing<T>> = object: Iterator<Listing<T>> {
        override fun hasNext() = _current != null && _current!!.after != null
        override fun next() = this@Paginator.next()
    }

    /** Constructs a new [Builder] with the current pagination settings */
    abstract fun newBuilder(): B

    /**
     * Base class for all Paginator.Builder subclasses
     */
    abstract class Builder<T : Thing>(val reddit: RedditClient, val baseUrl: String) {
        protected var timePeriod: TimePeriod = Paginator.DEFAULT_TIME_PERIOD
        protected var sorting = Paginator.DEFAULT_SORTING
        protected var limit = Paginator.DEFAULT_LIMIT // reddit returns 25 items when no limit parameter is passed

        fun sorting(sorting: Sorting): Builder<T> { this.sorting = sorting; return this }
        fun timePeriod(timePeriod: TimePeriod): Builder<T> { this.timePeriod = timePeriod; return this }
        fun limit(limit: Int): Builder<T> { this.limit = limit; return this }

        abstract fun build(): Paginator<T, Builder<T>>
    }

    companion object {
        /**
         * The recommended maximum limit of Things to return. No client-side code is in place to ensure that the limit is
         * not set over this number, but the Reddit API will only return this many amount of objects.
         */
        const val RECOMMENDED_MAX_LIMIT = 100
        const val DEFAULT_LIMIT = 25
        @JvmField val DEFAULT_SORTING = Sorting.NEW
        @JvmField val DEFAULT_TIME_PERIOD = TimePeriod.DAY
    }
}
