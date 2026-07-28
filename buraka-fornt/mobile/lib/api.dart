import 'dart:convert';

import 'package:http/http.dart' as http;

import 'models.dart';

class BurakaApi {
  final String catalogBase;
  final String reviewBase;

  BurakaApi({
    this.catalogBase = 'http://localhost:8081',
    this.reviewBase = 'http://localhost:8082',
  });

  Future<List<Product>> fetchProducts({int page = 0, int size = 20}) async {
    final res = await http.get(Uri.parse('$catalogBase/api/products?page=$page&size=$size'));
    if (res.statusCode != 200) {
      throw Exception('products fetch failed: ${res.statusCode}');
    }
    final body = jsonDecode(res.body) as Map<String, dynamic>;
    return (body['content'] as List)
        .map((e) => Product.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<Product> fetchProduct(String slug) async {
    final res = await http.get(Uri.parse('$catalogBase/api/products/$slug'));
    if (res.statusCode != 200) {
      throw Exception('product fetch failed: ${res.statusCode}');
    }
    return Product.fromJson(jsonDecode(res.body) as Map<String, dynamic>);
  }

  Future<List<Review>> fetchReviews(int productId) async {
    final res = await http.get(Uri.parse('$reviewBase/api/reviews?productId=$productId'));
    if (res.statusCode != 200) {
      throw Exception('reviews fetch failed: ${res.statusCode}');
    }
    return (jsonDecode(res.body) as List)
        .map((e) => Review.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<void> createReview({
    required int productId,
    required String title,
    required int rating,
    int userId = 1,
  }) async {
    final res = await http.post(
      Uri.parse('$reviewBase/api/reviews'),
      headers: {'Content-Type': 'application/json', 'X-User-Id': '$userId'},
      body: jsonEncode({'productId': productId, 'title': title, 'rating': rating}),
    );
    if (res.statusCode != 201) {
      throw Exception('review create failed: ${res.statusCode}');
    }
  }
}
