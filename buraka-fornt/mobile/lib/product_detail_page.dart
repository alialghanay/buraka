import 'package:flutter/material.dart';

import 'api.dart';
import 'models.dart';

class ProductDetailPage extends StatefulWidget {
  final String slug;
  final BurakaApi api;

  const ProductDetailPage({super.key, required this.slug, required this.api});

  @override
  State<ProductDetailPage> createState() => _ProductDetailPageState();
}

class _ProductDetailPageState extends State<ProductDetailPage> {
  Product? product;
  List<Review> reviews = [];
  String? error;

  final TextEditingController titleController = TextEditingController();
  int rating = 5;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final p = await widget.api.fetchProduct(widget.slug);
      final r = await widget.api.fetchReviews(p.productId);
      setState(() {
        product = p;
        reviews = r;
        error = null;
      });
    } catch (e) {
      setState(() => error = e.toString());
    }
  }

  Future<void> _submitReview() async {
    if (product == null || titleController.text.trim().isEmpty) return;
    try {
      await widget.api.createReview(
        productId: product!.productId,
        title: titleController.text.trim(),
        rating: rating,
      );
      titleController.clear();
      // Allow the ReviewCreated event to propagate before reloading.
      await Future.delayed(const Duration(milliseconds: 800));
      await _load();
    } catch (e) {
      setState(() => error = e.toString());
    }
  }

  @override
  Widget build(BuildContext context) {
    final p = product;
    return Scaffold(
      appBar: AppBar(title: Text(p?.title ?? 'Product')),
      body: error != null
          ? Center(child: Text('Error: $error'))
          : p == null
              ? const Center(child: CircularProgressIndicator())
              : ListView(
                  padding: const EdgeInsets.all(16),
                  children: [
                    Text(p.title, style: Theme.of(context).textTheme.headlineSmall),
                    const SizedBox(height: 8),
                    Text(p.description),
                    const SizedBox(height: 8),
                    Text('Price: ${(p.price / 100).toStringAsFixed(2)} LYD · In stock: ${p.quantity}'),
                    Text('★ ${p.ratingsAverage.toStringAsFixed(1)} (${p.ratingsQuantity} reviews)'),
                    const Divider(height: 32),
                    Text('Reviews', style: Theme.of(context).textTheme.titleMedium),
                    if (reviews.isEmpty) const Text('No reviews yet.'),
                    for (final r in reviews)
                      ListTile(
                        dense: true,
                        title: Text(r.title),
                        subtitle: Text('user ${r.userId}'),
                        trailing: Text('★ ${r.rating}'),
                      ),
                    const Divider(height: 32),
                    Text('Write a review', style: Theme.of(context).textTheme.titleMedium),
                    TextField(
                      controller: titleController,
                      maxLength: 200,
                      decoration: const InputDecoration(labelText: 'Review title'),
                    ),
                    DropdownButton<int>(
                      value: rating,
                      items: [5, 4, 3, 2, 1]
                          .map((r) => DropdownMenuItem(value: r, child: Text('$r★')))
                          .toList(),
                      onChanged: (v) => setState(() => rating = v ?? 5),
                    ),
                    ElevatedButton(onPressed: _submitReview, child: const Text('Submit')),
                  ],
                ),
    );
  }
}
