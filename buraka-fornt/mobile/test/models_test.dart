import 'package:buraka_mobile/models.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('Product.fromJson parses all fields', () {
    final product = Product.fromJson({
      'productId': 1,
      'slug': 'product-1',
      'title': 'Product 1',
      'description': 'desc',
      'quantity': 100,
      'sold': 0,
      'price': 1000,
      'imageCover': 'img',
      'ratingsAverage': 4.5,
      'ratingsQuantity': 2,
    });

    expect(product.slug, 'product-1');
    expect(product.ratingsAverage, 4.5);
    expect(product.ratingsQuantity, 2);
  });

  test('Product.fromJson tolerates integer ratingsAverage', () {
    final product = Product.fromJson({
      'productId': 1,
      'slug': 'product-1',
      'title': 'Product 1',
      'description': 'desc',
      'quantity': 100,
      'sold': 0,
      'price': 1000,
      'imageCover': 'img',
      'ratingsAverage': 0,
      'ratingsQuantity': 0,
    });

    expect(product.ratingsAverage, 0.0);
  });

  test('Review.fromJson parses all fields', () {
    final review = Review.fromJson({
      'reviewId': 1,
      'userId': 7,
      'productId': 10,
      'title': 'Great',
      'rating': 5,
      'createdAt': '2026-07-28T10:00:00',
    });

    expect(review.title, 'Great');
    expect(review.rating, 5);
  });
}
