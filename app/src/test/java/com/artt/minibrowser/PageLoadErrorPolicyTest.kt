package com.artt.minibrowser

import com.artt.minibrowser.engine.PageLoadError
import com.artt.minibrowser.engine.pageLoadErrorForCategory
import org.mozilla.geckoview.WebRequestError
import kotlin.test.Test
import kotlin.test.assertEquals

class PageLoadErrorPolicyTest {
    @Test
    fun mapsSecurityAndNetworkCategories() {
        assertEquals(
            PageLoadError.Security,
            pageLoadErrorForCategory(WebRequestError.ERROR_CATEGORY_SECURITY),
        )
        assertEquals(
            PageLoadError.Network,
            pageLoadErrorForCategory(WebRequestError.ERROR_CATEGORY_NETWORK),
        )
    }

    @Test
    fun unknownCategoryUsesGenericError() {
        assertEquals(PageLoadError.Generic, pageLoadErrorForCategory(Int.MIN_VALUE))
    }
}
