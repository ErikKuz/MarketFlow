# Статусы заказа и работа продавцов

Один `OrderEntity` может содержать товары нескольких продавцов. Для каждого продавца создаётся `SellerOrderEntity`.

```text
SellerOrder: NEW → PROCESSING → SELLERSENDPRODUCT → USERGETPRODUCT
Order:       CREATED → CONFIRMED → SELLERSSTARTWORK → SELLERSENDWORKANDSEND → COMPLETED
```

- `CREATED` / `NOT_PAID` появляются при оформлении.
- Успешная оплата переводит заказ в `CONFIRMED` / `PAID`.
- Первый продавец, начавший сборку, переводит общий заказ в `PROCESSING`.
- Общий заказ становится `SELLERSENDWORKANDSEND`, когда все части отправлены или уже получены.
- Покупатель подтверждает каждую доставленную часть.
- После всех `USERGETPRODUCT` общий заказ становится `COMPLETED`.

REST-действия:

- `POST /api/v1/seller/orders/{partId}/process`;
- `POST /api/v1/seller/orders/{partId}/ship`;
- `POST /api/v1/orders/{orderId}/fulfillments/{partId}/receive`.

Продавец не может обращаться к чужой части. Покупатель не может подтверждать часть чужого заказа. Недопустимые и обратные переходы отклоняются.

