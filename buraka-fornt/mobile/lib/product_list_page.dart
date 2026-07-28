import 'package:flutter/material.dart';

import 'api.dart';
import 'models.dart';
import 'product_detail_page.dart';

class ProductListPage extends StatefulWidget {
  const ProductListPage({super.key});

  @override
  State<ProductListPage> createState() => _ProductListPageState();
}

class _ProductListPageState extends State<ProductListPage> {
  final BurakaApi api = BurakaApi();
  late Future<List<Product>> future = api.fetchProducts();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Buraka Store')),
      body: FutureBuilder<List<Product>>(
        future: future,
        builder: (context, snapshot) {
          if (snapshot.hasError) {
            return Center(child: Text('Error: ${snapshot.error}\nIs catalog-service on :8081?'));
          }
          if (!snapshot.hasData) {
            return const Center(child: CircularProgressIndicator());
          }
          final products = snapshot.data!;
          return ListView.builder(
            itemCount: products.length,
            itemBuilder: (context, index) {
              final p = products[index];
              return ListTile(
                title: Text(p.title),
                subtitle: Text('${(p.price / 100).toStringAsFixed(2)} LYD'),
                trailing: Text('★ ${p.ratingsAverage.toStringAsFixed(1)} (${p.ratingsQuantity})'),
                onTap: () => Navigator.push(
                  context,
                  MaterialPageRoute(builder: (_) => ProductDetailPage(slug: p.slug, api: api)),
                ),
              );
            },
          );
        },
      ),
    );
  }
}
