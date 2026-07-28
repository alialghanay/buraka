class Product {
  final int productId;
  final String slug;
  final String title;
  final String description;
  final int quantity;
  final int sold;
  final int price;
  final String imageCover;
  final double ratingsAverage;
  final int ratingsQuantity;

  Product({
    required this.productId,
    required this.slug,
    required this.title,
    required this.description,
    required this.quantity,
    required this.sold,
    required this.price,
    required this.imageCover,
    required this.ratingsAverage,
    required this.ratingsQuantity,
  });

  factory Product.fromJson(Map<String, dynamic> json) => Product(
        productId: json['productId'] as int,
        slug: json['slug'] as String,
        title: json['title'] as String,
        description: json['description'] as String? ?? '',
        quantity: json['quantity'] as int,
        sold: json['sold'] as int,
        price: json['price'] as int,
        imageCover: json['imageCover'] as String? ?? '',
        ratingsAverage: (json['ratingsAverage'] as num).toDouble(),
        ratingsQuantity: json['ratingsQuantity'] as int,
      );
}

class Review {
  final int reviewId;
  final int userId;
  final int productId;
  final String title;
  final int rating;

  Review({
    required this.reviewId,
    required this.userId,
    required this.productId,
    required this.title,
    required this.rating,
  });

  factory Review.fromJson(Map<String, dynamic> json) => Review(
        reviewId: json['reviewId'] as int,
        userId: json['userId'] as int,
        productId: json['productId'] as int,
        title: json['title'] as String,
        rating: json['rating'] as int,
      );
}
