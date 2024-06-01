package woowacourse.shopping.presentation.ui.shoppingcart.orderrecommend

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import woowacourse.shopping.domain.model.Cart
import woowacourse.shopping.domain.model.Product
import woowacourse.shopping.domain.repository.OrderRepository
import woowacourse.shopping.domain.repository.ProductHistoryRepository
import woowacourse.shopping.domain.repository.ShoppingCartRepository
import woowacourse.shopping.presentation.base.BaseViewModel
import woowacourse.shopping.presentation.base.BaseViewModelFactory
import woowacourse.shopping.presentation.base.Event
import woowacourse.shopping.presentation.base.emit
import woowacourse.shopping.presentation.common.ProductCountHandler
import kotlin.concurrent.thread

class OrderRecommendViewModel(
    private val productHistoryRepository: ProductHistoryRepository,
    private val shoppingCartRepository: ShoppingCartRepository,
    private val orderRepository: OrderRepository,
) : BaseViewModel(), ProductCountHandler {
    private val _uiState: MutableLiveData<OrderRecommendUiState> =
        MutableLiveData(OrderRecommendUiState())
    val uiState: LiveData<OrderRecommendUiState> get() = _uiState

    private val _navigateAction: MutableLiveData<Event<OrderRecommendNavigateAction>> =
        MutableLiveData(null)
    val navigateAction: LiveData<Event<OrderRecommendNavigateAction>> get() = _navigateAction

    init {
        recommendProductLoad()
    }

    private fun recommendProductLoad() {
        thread {
            productHistoryRepository.getRecommendedProducts(10).onSuccess { recommendProducts ->
                hideError()
                _uiState.value?.let { state ->
                    _uiState.postValue(state.copy(recommendCarts = recommendProducts))
                }
            }.onFailure { e -> showError(e) }
        }
    }

    fun load(orderCarts: List<Cart>) {
        _uiState.value?.let { state ->
            _uiState.value = state.copy(orderCarts = orderCarts)
        }
    }

    override fun retry() {}

    fun order() {
        thread {
            val state = uiState.value ?: return@thread
            orderRepository.insertOrder(state.orderCarts.map { it.id }).onSuccess {
                hideError()
                _navigateAction.emit(OrderRecommendNavigateAction.NavigateToProductList)
            }.onFailure { e ->
                showError(e)
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
        val state = uiState.value ?: return

        val updatedRecommendCarts =
            state.recommendCarts.map { cart ->
                if (cart.product.id == productId) {
                    updateCart(cart, increment)
                } else {
                    cart
                }
            }

        _uiState.value =
            state.copy(
                recommendCarts = updatedRecommendCarts,
                orderCarts = state.orderCarts,
            )
    }

    private fun updateCart(
        cart: Cart,
        increment: Boolean,
    ): Cart {
        val updatedQuantity = calculateUpdatedQuantity(cart.quantity, increment)
        calculatedUpdateCart(cart, updatedQuantity)
        return cart.copy(quantity = updatedQuantity)
    }

    private fun calculateUpdatedQuantity(
        currentQuantity: Int,
        increment: Boolean,
    ): Int {
        return if (increment) currentQuantity + 1 else currentQuantity - 1
    }

    private fun calculatedUpdateCart(
        cart: Cart,
        updatedQuantity: Int,
    ) {
        if (cart.quantity == Cart.INIT_QUANTITY_NUM) {
            insertCartProduct(cart.product, updatedQuantity)
        } else if (updatedQuantity == Cart.INIT_QUANTITY_NUM) {
            deleteCartProduct(cart)
        } else {
            updateCartProduct(cart, updatedQuantity)
        }
    }

    private fun insertCartProduct(
        product: Product,
        quantity: Int,
    ) {
        thread {
            shoppingCartRepository.postCartItem(
                productId = product.id,
                quantity = quantity,
            ).onSuccess { cartItemId ->
                hideError()
                var state = uiState.value ?: return@onSuccess
                val updateRecommendCarts =
                    state.recommendCarts.map { cart ->
                        if (cart.product.id == product.id) {
                            val insertCart = cart.copy(id = cartItemId.id, quantity = 1)
                            state = state.copy(orderCarts = state.orderCarts + insertCart)
                            insertCart
                        } else {
                            cart
                        }
                    }

                _uiState.postValue(
                    state.copy(
                        recommendCarts = updateRecommendCarts,
                        orderCarts = state.orderCarts,
                    ),
                )
            }.onFailure { e ->
                showError(e)
            }
        }
    }

    private fun deleteCartProduct(cart: Cart) {
        thread {
            shoppingCartRepository.deleteCartItem(
                cartId = cart.id,
            ).onSuccess {
                val state = uiState.value ?: return@onSuccess
                _uiState.postValue(state.copy(orderCarts = state.orderCarts - cart))
            }.onFailure { e ->
                showError(e)
            }
        }
    }

    private fun updateCartProduct(
        cart: Cart,
        quantity: Int,
    ) {
        thread {
            shoppingCartRepository.patchCartItem(
                cartId = cart.id,
                quantity = quantity,
            ).onSuccess {
                var state = uiState.value ?: return@onSuccess
                state = state.copy(orderCarts = state.orderCarts - cart)
                state = state.copy(orderCarts = state.orderCarts + cart.copy(quantity = quantity))
                _uiState.postValue(state.copy(orderCarts = state.orderCarts))
            }.onFailure { e ->
                showError(e)
            }
        }
    }

    companion object {
        fun factory(
            productHistoryRepository: ProductHistoryRepository,
            shoppingCartRepository: ShoppingCartRepository,
            orderRepository: OrderRepository,
        ): ViewModelProvider.Factory {
            return BaseViewModelFactory {
                OrderRecommendViewModel(
                    productHistoryRepository,
                    shoppingCartRepository,
                    orderRepository,
                )
            }
        }
    }
}
