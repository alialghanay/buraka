import 'package:flutter/material.dart';

import 'product_list_page.dart';

void main() {
  runApp(const BurakaApp());
}

class BurakaApp extends StatelessWidget {
  const BurakaApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Buraka Store',
      theme: ThemeData(colorSchemeSeed: Colors.teal, useMaterial3: true),
      home: const ProductListPage(),
    );
  }
}
