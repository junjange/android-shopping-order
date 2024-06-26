package woowacourse.shopping.presentation.ui.productlist

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import woowacourse.shopping.domain.model.Cart
import woowacourse.shopping.domain.model.Product
import woowacourse.shopping.domain.repository.ProductHistoryRepository
import woowacourse.shopping.domain.repository.ProductRepository
import woowacourse.shopping.domain.repository.ShoppingCartRepository
import woowacourse.shopping.presentation.base.BaseViewModel
import woowacourse.shopping.presentation.base.BaseViewModelFactory
import woowacourse.shopping.presentation.base.Event
import woowacourse.shopping.presentation.base.MessageProvider
import woowacourse.shopping.presentation.base.emit
import woowacourse.shopping.presentation.common.ProductCountHandler
import woowacourse.shopping.presentation.ui.productlist.adapter.ProductListPagingSource
import kotlin.concurrent.thread

class ProductListViewModel(
    private val productRepository: ProductRepository,
    private val shoppingCartRepository: ShoppingCartRepository,
    private val productHistoryRepository: ProductHistoryRepository,
) : BaseViewModel(), ProductListActionHandler, ProductCountHandler {
    private val _uiState: MutableLiveData<ProductListUiState> =
        MutableLiveData(ProductListUiState())
    val uiState: LiveData<ProductListUiState> get() = _uiState

    private val _navigateAction: MutableLiveData<Event<ProductListNavigateAction>> =
        MutableLiveData(null)
    val navigateAction: LiveData<Event<ProductListNavigateAction>> get() = _navigateAction

    private val productListPagingSource =
        ProductListPagingSource(
            productRepository = productRepository,
            shoppingCartRepository = shoppingCartRepository,
        )

    init {
        initLoad()
    }

    private fun initLoad() {
        thread {
            showLoading()
            Thread.sleep(1000) // TODO 스켈레톤 UI를 보여주기 위한 sleep..zzz
            productListPagingSource.load().mapCatching { pagingProduct ->
                val productHistories =
                    productHistoryRepository.getProductHistory(10).getOrDefault(emptyList())
                val cartCount = shoppingCartRepository.getCartProductsTotal().getOrDefault(0)

                ProductListUiState(
                    pagingCart = pagingProduct,
                    productHistories = productHistories,
                    cartQuantity = cartCount,
                )
            }.onSuccess { productListUiState ->
                hideError()
                _uiState.postValue(productListUiState)
            }.onFailure { e ->
                showError(e)
                showMessage(MessageProvider.DefaultErrorMessage)
            }

            Thread.sleep(1000) // TODO 스켈레톤 UI를 보여주기 위한 sleep..zzz
            hideLoading()
        }
    }

    override fun retry() {
        loadMoreProducts()
    }

    override fun navigateToProductDetail(productId: Long) {
        _navigateAction.emit(ProductListNavigateAction.NavigateToProductDetail(productId = productId))
    }

    fun navigateToShoppingCart() {
        _navigateAction.emit(ProductListNavigateAction.NavigateToShoppingCart)
    }

    override fun loadMoreProducts() {
        thread {
            productListPagingSource.load().onSuccess { pagingProduct ->
                hideError()
                _uiState.value?.let { state ->
                    val nowPagingCart =
                        PagingCart(
                            cartList = state.pagingCart.cartList + pagingProduct.cartList,
                            last = pagingProduct.last,
                        )
                    val cartCount = nowPagingCart.cartList.sumOf { it.quantity }

                    _uiState.postValue(
                        state.copy(
                            pagingCart = nowPagingCart,
                            cartQuantity = cartCount,
                        ),
                    )
                }
            }.onFailure { e ->
                showError(e)
                showMessage(MessageProvider.DefaultErrorMessage)
            }
        }
    }

    fun updateProducts() {
        thread {
            uiState.value?.let { state ->
                val cartProducts = shoppingCartRepository.getAllCarts().getOrNull()
                val updatedProductList =
                    state.pagingCart.cartList.map { cart ->
                        val matchingCartProduct =
                            cartProducts?.content?.find { it.product.id == cart.product.id }

                        cart.copy(
                            id = matchingCartProduct?.id ?: 0,
                            quantity = matchingCartProduct?.quantity ?: 0,
                        )
                    }

                val productHistories =
                    productHistoryRepository.getProductHistory(10).getOrDefault(emptyList())
                val cartCount = shoppingCartRepository.getCartProductsTotal().getOrDefault(0)

                _uiState.postValue(
                    state.copy(
                        pagingCart = PagingCart(updatedProductList),
                        productHistories = productHistories,
                        cartQuantity = cartCount,
                    ),
                )
            }
        }
    }

    override fun plusProductQuantity(
        productId: Long,
        position: Int,
    ) {
        updateProductQuantity(productId, increment = true)
    }

    override fun minusProductQuantity(
        productId: Long,
        position: Int,
    ) {
        updateProductQuantity(productId, increment = false)
    }

    private fun updateProductQuantity(
        productId: Long,
        increment: Boolean,
    ) {
        _uiState.value?.let { state ->
            val updatedProductList =
                state.pagingCart.cartList.map { cart ->
                    if (cart.product.id == productId) {
                        cart.updateProduct(increment)
                    } else {
                        cart
                    }
                }
            val updatedCartCount = updatedProductList.sumOf { it.quantity }
            _uiState.postValue(
                state.copy(
                    pagingCart = PagingCart(updatedProductList),
                    cartQuantity = updatedCartCount,
                ),
            )
        }
    }

    private fun Cart.updateProduct(increment: Boolean): Cart {
        val updatedQuantity = if (increment) this.quantity + 1 else this.quantity - 1
        when {
            this.quantity == 0 -> insertCartProduct(this.product, updatedQuantity)
            updatedQuantity == 0 -> deleteCartProduct(this.id)
            else -> updateCartProduct(this.id, updatedQuantity)
        }
        return this.copy(quantity = updatedQuantity)
    }

    private fun insertCartProduct(
        product: Product,
        quantity: Int,
    ) {
        thread {
            shoppingCartRepository.insertCartProduct(
                productId = product.id,
                quantity = quantity,
            ).onSuccess { cartId ->
                hideError()
                _uiState.value?.let { state ->
                    val updateCartList =
                        state.pagingCart.cartList.map { cart ->
                            if (cart.product.id == product.id) {
                                cart.copy(id = cartId)
                            } else {
                                cart
                            }
                        }

                    val pagingCart = PagingCart(cartList = updateCartList)

                    _uiState.postValue(
                        state.copy(pagingCart = pagingCart),
                    )
                }
            }.onFailure { e ->
                showError(e)
            }
        }
    }

    private fun deleteCartProduct(cartId: Int) {
        thread {
            shoppingCartRepository.deleteCartProduct(
                cartId = cartId,
            ).onFailure { e ->
                showError(e)
            }
        }
    }

    private fun updateCartProduct(
        cartId: Int,
        quantity: Int,
    ) {
        thread {
            shoppingCartRepository.updateCartProduct(
                cartId = cartId,
                quantity = quantity,
            ).onFailure { e ->
                showError(e)
            }
        }
    }

    companion object {
        fun factory(
            productRepository: ProductRepository,
            shoppingCartRepository: ShoppingCartRepository,
            productHistoryRepository: ProductHistoryRepository,
        ): ViewModelProvider.Factory {
            return BaseViewModelFactory {
                ProductListViewModel(
                    productRepository,
                    shoppingCartRepository,
                    productHistoryRepository,
                )
            }
        }
    }
}
