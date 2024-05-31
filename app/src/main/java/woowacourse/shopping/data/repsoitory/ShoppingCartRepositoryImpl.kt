package woowacourse.shopping.data.repsoitory

import woowacourse.shopping.data.datasource.remote.ShoppingCartDataSource
import woowacourse.shopping.data.mapper.toDomain
import woowacourse.shopping.domain.model.CartItemId
import woowacourse.shopping.domain.model.Carts
import woowacourse.shopping.domain.repository.ShoppingCartRepository

class ShoppingCartRepositoryImpl(private val dataSource: ShoppingCartDataSource) :
    ShoppingCartRepository {
    override fun insertCartProduct(
        productId: Long,
        quantity: Int,
    ): Result<CartItemId> =
        dataSource.insertCartProduct(
            productId = productId,
            quantity = quantity,
        ).mapCatching { it.toDomain() }

    override fun updateCartProduct(
        cartId: Int,
        quantity: Int,
    ): Result<Unit> = dataSource.updateCartProduct(cartId = cartId, quantity = quantity)

    override fun getCartProductsPaged(
        page: Int,
        size: Int,
    ): Result<Carts> =
        dataSource.getCartProductsPaged(page = page, size = size)
            .mapCatching { result -> result.toDomain() }

    override fun getCartProductsTotal(): Result<Int> = dataSource.getCartProductsTotal()

    override fun deleteCartProduct(cartId: Int): Result<Unit> = dataSource.deleteCartProduct(cartId = cartId)

    override fun getAllCarts(): Result<Carts> {
        val totalElements =
            dataSource.getCartProductsPaged(
                page = ProductRepositoryImpl.FIRST_PAGE,
                size = ProductRepositoryImpl.FIRST_SIZE,
            ).getOrThrow().totalElements

        return dataSource.getCartProductsPaged(
            page = ProductRepositoryImpl.FIRST_PAGE,
            size = totalElements,
        ).mapCatching { it.toDomain() }
    }

    companion object {
        private var instance: ShoppingCartRepositoryImpl? = null

        fun setInstance(dataSource: ShoppingCartDataSource) {
            instance = ShoppingCartRepositoryImpl(dataSource = dataSource)
        }

        fun getInstance(): ShoppingCartRepositoryImpl = requireNotNull(instance)
    }
}
