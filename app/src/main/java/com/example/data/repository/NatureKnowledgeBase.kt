package com.example.data.repository

import com.example.data.model.SpeciesCategory
import com.example.data.model.SpeciesInfo
import java.util.UUID

object NatureKnowledgeBase {

    val SAMPLE_SPECIES: List<SpeciesInfo> = listOf(
        // 1. Monstera Deliciosa (Trầu bà lá xẻ Nam Mỹ)
        SpeciesInfo(
            id = "monstera_deliciosa",
            commonNameEn = "Swiss Cheese Plant (Monstera)",
            commonNameVi = "Trầu bà lá xẻ (Monstera)",
            scientificName = "Monstera deliciosa",
            category = SpeciesCategory.PLANT.name,
            kingdom = "Plantae",
            family = "Araceae (Họ Ráy)",
            orderName = "Alismatales (Bộ Trạch tả)",
            descriptionEn = "An evergreen tropical climbing vine famous for its huge perforated leaves resembling Swiss cheese slices.",
            descriptionVi = "Cây thân leo biểu sinh nhiệt đới nổi tiếng với những phiến lá xẻ lỗ độc đáo tựa như phô mai Thụy Sĩ.",
            habitatEn = "Tropical rainforest understory in Central and South America.",
            habitatVi = "Tầng dưới tán rừng mưa nhiệt đới Trung và Nam Mỹ.",
            distributionEn = "Native to Mexico & Central America, cultivated globally as an ornamental.",
            distributionVi = "Bản địa Mexico & Trung Mỹ, nay trồng phổ biến làm cây cảnh phong thủy toàn cầu.",
            ecologicalRoleEn = "Provides canopy shelter and climbs large host trees using aerial roots.",
            ecologicalRoleVi = "Cung cấp bóng mát, hỗ trợ hệ vi sinh và leo bám nhờ rễ khí sinh chắc khỏe.",
            mysteriaFactEn = "The leaf fenestrations (holes) allow high winds to pass through without tearing the leaves, and let sunbeams reach lower leaves!",
            mysteriaFactVi = "Các lỗ xẻ trên phiến lá giúp giảm sức cản khi mưa bão nhiệt đới và cho phép ánh sáng lọt xuống nuôi các lá non phía dưới!",
            conservationStatusCode = "LC",
            toxicityOrCareEn = "Contains calcium oxalate crystals. Keep away from pets. Prefers indirect bright light.",
            toxicityOrCareVi = "Lá chứa canxi oxalat, tránh cho thú cưng cắn. Ưa ánh sáng gián tiếp và độ ẩm vừa phải.",
            confidenceScore = 98
        ),

        // 2. Sacred Lotus (Hoa Sen)
        SpeciesInfo(
            id = "nelumbo_nucifera",
            commonNameEn = "Sacred Lotus",
            commonNameVi = "Hoa Sen Hồng",
            scientificName = "Nelumbo nucifera",
            category = SpeciesCategory.PLANT.name,
            kingdom = "Plantae",
            family = "Nelumbonaceae (Họ Sen)",
            orderName = "Proteales (Bộ Quắn hoa)",
            descriptionEn = "An aquatic perennial plant with majestic pink/white flowers and circular floating leaves.",
            descriptionVi = "Loài thực vật thủy sinh sống lâu năm với những đóa hoa hồng/trắng thanh khiết và lá tròn nổi trên mặt nước.",
            habitatEn = "Slow-moving rivers, freshwater lakes, ponds, and wetlands.",
            habitatVi = "Ao hồ, đầm lầy, sông suối nước ngọt chảy chậm khắp châu Á.",
            distributionEn = "Native to tropical Asia and Queensland, Australia. National flower of Vietnam & India.",
            distributionVi = "Bản địa châu Á nhiệt đới, quốc hoa biểu tượng của Việt Nam và Ấn Độ.",
            ecologicalRoleEn = "Purifies water bodies, provides habitat for fish and macroinvertebrates.",
            ecologicalRoleVi = "Lọc sạch trầm tích bùn nước, cung cấp bóng mát và nơi trú ẩn cho cá tôm.",
            mysteriaFactEn = "Lotus leaves possess the famous 'Lotus Effect' (superhydrophobicity)—water droplets roll off carrying all dirt particles cleanly!",
            mysteriaFactVi = "Lá sen sở hữu cấu trúc nano siêu kỵ nước ('Hiệu ứng lá sen') khiến giọt nước vo tròn và cuốn trôi mọi bụi bẩn khi lăn qua!",
            conservationStatusCode = "LC",
            toxicityOrCareEn = "Completely edible: roots (củ sen), seeds (hạt sen), and petals (trà sen).",
            toxicityOrCareVi = "Toàn bộ cây đều có giá trị: củ sen, ngó sen, hạt sen bổ dưỡng và cánh hoa ướp trà thanh tao.",
            confidenceScore = 99
        ),

        // 3. Indochinese Tiger (Hổ Đông Dương)
        SpeciesInfo(
            id = "panthera_tigris_corbetti",
            commonNameEn = "Indochinese Tiger",
            commonNameVi = "Hổ Đông Dương (Chúa sơn lâm)",
            scientificName = "Panthera tigris corbetti",
            category = SpeciesCategory.ANIMAL.name,
            kingdom = "Animalia",
            family = "Felidae (Họ Mèo)",
            orderName = "Carnivora (Bộ Ăn thịt)",
            descriptionEn = "A magnificent apex predator with dark orange coat, narrow black stripes, and muscular physique.",
            descriptionVi = "Loài động vật săn mồi đầu bảng oai phong với bộ lông vàng cam rực rỡ xen kẽ sọc vằn đen huyền bí.",
            habitatEn = "Tropical, subtropical moist broadleaf and evergreen mountain forests of Indochina.",
            habitatVi = "Rừng rậm nguyên sinh lá rộng thường xanh và vùng núi cao bán đảo Đông Dương.",
            distributionEn = "Myanmar, Thailand, Laos, Vietnam, Cambodia.",
            distributionVi = "Vùng rừng núi Đông Dương (Việt Nam, Lào, Campuchia, Thái Lan, Myanmar).",
            ecologicalRoleEn = "Apex predator that regulates ungulate prey populations and preserves forest ecosystem balance.",
            ecologicalRoleVi = "Động vật đầu chuỗi thức ăn, kiểm soát quần thể động vật ăn cỏ để bảo vệ sự cân bằng thảm thực vật.",
            mysteriaFactEn = "Every tiger has a unique stripe pattern like human fingerprints, and their skin beneath the fur is also striped!",
            mysteriaFactVi = "Mỗi cá thể hổ sở hữu hoa văn sọc vằn độc nhất vô nhị như vân tay con người, và lớp da dưới lông cũng có sọc vằn y hệt!",
            conservationStatusCode = "CR",
            toxicityOrCareEn = "Critically endangered. Strictly protected under international law CITES Appendix I.",
            toxicityOrCareVi = "Cực kỳ nguy cấp. Được bảo tồn nghiêm ngặt trong Sách Đỏ Việt Nam và Quốc tế.",
            confidenceScore = 96
        ),

        // 4. Red-shanked Douc Langur (Voọc chà vá chân nâu)
        SpeciesInfo(
            id = "pygathrix_nemaeus",
            commonNameEn = "Red-shanked Douc Langur",
            commonNameVi = "Voọc Chà Vá Chân Nâu (Nữ hoàng linh trưởng)",
            scientificName = "Pygathrix nemaeus",
            category = SpeciesCategory.ANIMAL.name,
            kingdom = "Animalia",
            family = "Cercopithecidae (Họ Khỉ cựu thế giới)",
            orderName = "Primates (Bộ Linh trưởng)",
            descriptionEn = "Often called the 'Queen of Primates' for its striking colorful fur: maroon legs, white forearms, orange face.",
            descriptionVi = "Được mệnh danh là 'Nữ hoàng linh trưởng' nhờ 5 sắc lông rực rỡ: cẳng chân nâu đỏ, tay trắng, mặt vàng cam.",
            habitatEn = "High canopy of primary semi-evergreen and rainforests, especially Son Tra Peninsula.",
            habitatVi = "Tầng tán cao của rừng nguyên sinh nhiệt đới, nổi tiếng tại bán đảo Sơn Trà (Đà Nẵng).",
            distributionEn = "Endemic to Indochina (Central Vietnam, Laos, Cambodia).",
            distributionVi = "Đặc hữu vùng Trung Trường Sơn Việt Nam và Trung Lào.",
            ecologicalRoleEn = "Crucial seed disperser for high-altitude native jungle trees.",
            ecologicalRoleVi = "Phát tán hạt giống cây rừng tự nhiên, duy trì sự tái sinh của thảm rừng già nhiệt đới.",
            mysteriaFactEn = "They have complex multi-chambered stomachs containing specialized fermenting bacteria to digest tough cellulose leaves!",
            mysteriaFactVi = "Voọc có dạ dày nhiều ngăn chứa hệ vi khuẩn lên men đặc biệt để tiêu hóa lá cây giàu chất xơ mà các loài khỉ khác không ăn được!",
            conservationStatusCode = "CR",
            toxicityOrCareEn = "Extremely vulnerable to habitat fragmentation and illegal poaching.",
            toxicityOrCareVi = "Bảo tồn tối cấp. Biểu tượng đa dạng sinh học độc đáo của bán đảo Sơn Trà.",
            confidenceScore = 97
        ),

        // 5. Great Hornbill (Chim Hồng hoàng / Phượng hoàng đất)
        SpeciesInfo(
            id = "buceros_bicornis",
            commonNameEn = "Great Hornbill",
            commonNameVi = "Chim Hồng Hoàng (Phượng hoàng đất)",
            scientificName = "Buceros bicornis",
            category = SpeciesCategory.BIRD.name,
            kingdom = "Animalia",
            family = "Bucerotidae (Họ Hồng hoàng)",
            orderName = "Bucerotiformes (Bộ Hồng hoàng)",
            descriptionEn = "A massive iconic forest bird with a bright yellow and black casque atop its massive curved bill.",
            descriptionVi = "Loài chim rừng khổng lồ nổi bật với chiếc mỏ cong lớn mang một cấu trúc sừng (casque) màu vàng óng như chiếc mũ miện.",
            habitatEn = "Dense undisturbed primary evergreen forests of South and Southeast Asia.",
            habitatVi = "Rừng già nguyên sinh nhiệt đới tầng tán cao vùng Đông Nam Á.",
            distributionEn = "Indian subcontinent, Southeast Asia (Vietnam, Thailand, Malaysia).",
            distributionVi = "Khu bảo tồn Cát Tiên, Bạch Mã, Phong Nha - Kẻ Bàng và Đông Nam Á.",
            ecologicalRoleEn = "Considered the 'farmer of the forest' due to spreading large fruit seeds across vast distances.",
            ecologicalRoleVi = "Được gọi là 'Kỹ sư kiến tạo rừng' nhờ phát tán các hạt cây rừng gỗ lớn đi khắp các cánh rừng nguyên sinh.",
            mysteriaFactEn = "During nesting, the female seals herself inside a tree hollow with mud for months, fed solely by the devoted male!",
            mysteriaFactVi = "Khi ấp trứng, chim mái tự giam mình trong hốc cây và trát kín bùn chỉ chừa khe nhỏ; chim trống tận tụy bay đi kiếm ăn nuôi vợ con suốt 3 tháng!",
            conservationStatusCode = "VU",
            toxicityOrCareEn = "Requires ancient hollow trees for breeding.",
            toxicityOrCareVi = "Loài quý hiếm cần được bảo tồn các cây rừng cổ thụ rỗng ruột để làm tổ.",
            confidenceScore = 96
        ),

        // 6. Atlas Moth (Bướm Khế / Bướm Atlas khổng lồ)
        SpeciesInfo(
            id = "attacus_atlas",
            commonNameEn = "Atlas Moth",
            commonNameVi = "Bướm Khế (Bướm Đêm Atlas Khổng Lồ)",
            scientificName = "Attacus atlas",
            category = SpeciesCategory.INSECT.name,
            kingdom = "Animalia",
            family = "Saturniidae (Họ Bướm đêm hoàng đế)",
            orderName = "Lepidoptera (Bộ Cánh vẩy)",
            descriptionEn = "One of the largest lepidopterans in the world with a wingspan reaching up to 25–30 cm.",
            descriptionVi = "Một trong những loài bướm đêm lớn nhất hành tinh với sải cánh khổng lồ lên tới 25–30 cm mang hoa văn huyền bí.",
            habitatEn = "Tropical and subtropical lowland and montane forests.",
            habitatVi = "Vườn cây ăn trái, rừng thứ sinh và rừng nhiệt đới Đông Nam Á.",
            distributionEn = "South Asia, East Asia, and Southeast Asia.",
            distributionVi = "Phổ biến tại các vùng quê Việt Nam (thường đậu trên cây khế, ổi) và rừng Đông Nam Á.",
            ecologicalRoleEn = "Caterpillars consume foliage and silk cocoons enrich soil nutrients.",
            ecologicalRoleVi = "Góp phần vào chuỗi thức ăn cho chim chóc và thú ăn côn trùng.",
            mysteriaFactEn = "The tips of its wings closely mimic snake heads, complete with eyes and scales, to terrify avian predators!",
            mysteriaFactVi = "Chóp hai đầu cánh có hoa văn và đường cong ngụy trang y hệt đầu một con rắn hổ mang đang mở mắt đe dọa kẻ thù!",
            conservationStatusCode = "LC",
            toxicityOrCareEn = "Adult moths have no mouthparts and live only 1–2 weeks off stored caterpillar fat reserves.",
            toxicityOrCareVi = "Bướm trưởng thành không có vòi ăn, chỉ sống 1-2 tuần nhờ năng lượng tích lũy từ thời sâu bướm để giao phối và duy trì nòi giống.",
            confidenceScore = 95
        ),

        // 7. Banyan Tree (Cây Đa cổ thụ)
        SpeciesInfo(
            id = "ficus_benghalensis",
            commonNameEn = "Sacred Banyan Tree",
            commonNameVi = "Cây Đa (Đa Cổ Thụ)",
            scientificName = "Ficus benghalensis",
            category = SpeciesCategory.PLANT.name,
            kingdom = "Plantae",
            family = "Moraceae (Họ Dâu tằm)",
            orderName = "Rosales (Bộ Hoa hồng)",
            descriptionEn = "A colossal fig tree characterized by vast canopy spread and woody prop roots descending from branches.",
            descriptionVi = "Cây đại thụ linh thiêng với tán lá che rợp bóng mát và hàng trăm rễ phụ thả từ cành đâm sâu vào lòng đất.",
            habitatEn = "Monsoon forests, temple grounds, and rural riverbanks.",
            habitatVi = "Rừng mưa nhiệt đới, cổng làng, đền đài cổ kính khắp làng quê Việt Nam.",
            distributionEn = "Native to the Indian subcontinent and Southeast Asia.",
            distributionVi = "Gắn liền với văn hóa làng quê Việt Nam, cây đa Tân Trào, đa Sơn Trà hàng nghìn năm tuổi.",
            ecologicalRoleEn = "Keystone species providing figs all year round for hundreds of bird and mammal species.",
            ecologicalRoleVi = "Loài cây chủ chốt (Keystone species) cung cấp quả chín quanh năm nuôi sống hàng trăm loài chim muông và linh trưởng.",
            mysteriaFactEn = "A single banyan can resemble an entire forest by sending down thousands of pillar roots covering hectares of land!",
            mysteriaFactVi = "Một cá thể cây Đa duy nhất có thể vươn hàng nghìn rễ phụ tạo thành cả một 'khu rừng thu nhỏ' bao phủ diện tích rộng hàng hecta!",
            conservationStatusCode = "LC",
            toxicityOrCareEn = "Produce white milky latex. Highly durable and revered for environmental stability.",
            toxicityOrCareVi = "Nhựa mủ trắng có tính sát khuẩn nhẹ. Cây có sức sống bền bỉ hàng trăm đến hàng nghìn năm.",
            confidenceScore = 98
        ),

        // 8. Sunda Pangolin (Tê Tê Java)
        SpeciesInfo(
            id = "manis_javanica",
            commonNameEn = "Sunda Pangolin",
            commonNameVi = "Tê Tê Java (Trút)",
            scientificName = "Manis javanica",
            category = SpeciesCategory.ANIMAL.name,
            kingdom = "Animalia",
            family = "Manidae (Họ Tê tê)",
            orderName = "Pholidota (Bộ Tê tê)",
            descriptionEn = "A nocturnal mammal covered in protective keratin scales with a prehensile tail and long sticky tongue.",
            descriptionVi = "Loài thú ăn kiến độc đáo có lớp vảy sừng keratin cứng cáp bao bọc toàn thân và chiếc lưỡi siêu dính dài hơn cả cơ thể.",
            habitatEn = "Primary and secondary forests, rubber plantations, and scrublands.",
            habitatVi = "Rừng nhiệt đới ẩm, vườn cây rậm rạp và hốc đất khắp Việt Nam.",
            distributionEn = "Southeast Asia including Vietnam, Thailand, Malaysia, Indonesia.",
            distributionVi = "Vườn quốc gia Cúc Phương, Cát Tiên và các cánh rừng Đông Nam Á.",
            ecologicalRoleEn = "Consumes over 70 million ants and termites per year, protecting healthy forest trees.",
            ecologicalRoleVi = "Mỗi con tê tê tiêu thụ hơn 70 triệu con mối/kiến mỗi năm, giúp cứu hàng triệu cây rừng khỏi bị mối mọt đục rỗng!",
            mysteriaFactEn = "When threatened, it rolls into an impenetrable armored ball that even lions and tigers cannot bite open!",
            mysteriaFactVi = "Khi gặp nguy hiểm, tê tê cuộn tròn cơ thể thành một quả cầu bọc giáp thép bất khả xâm phạm khiến ngay cả hổ báo cũng đành bó tay!",
            conservationStatusCode = "CR",
            toxicityOrCareEn = "Critically endangered. Strictly protected by penal laws against trafficking.",
            toxicityOrCareVi = "Cực kỳ nguy cấp, bị săn bắt trái phép nghiêm trọng. Được cứu hộ tại Vườn Quốc Gia Cúc Phương.",
            confidenceScore = 96
        ),

        // 9. Venus Flytrap (Cây Bắt Ruồi)
        SpeciesInfo(
            id = "dionaea_muscipula",
            commonNameEn = "Venus Flytrap",
            commonNameVi = "Cây Bắt Ruồi (Cây Ăn Thịt)",
            scientificName = "Dionaea muscipula",
            category = SpeciesCategory.PLANT.name,
            kingdom = "Plantae",
            family = "Droseraceae (Họ Gọng vó)",
            orderName = "Caryophyllales (Bộ Cẩm chướng)",
            descriptionEn = "A carnivorous plant that catches insect prey with a specialized clam-shell trapping mechanism.",
            descriptionVi = "Loài thực vật ăn thịt kỳ lạ với những chiếc bẫy kẹp hình vỏ sò có gai nhọn cảm biến.",
            habitatEn = "Nitrogen-poor bogs and wet peat savannas.",
            habitatVi = "Đầm lầy than bùn nghèo dinh dưỡng và đất chua ẩm ướt.",
            distributionEn = "Native to coastal North Carolina, grown globally by botany hobbyists.",
            distributionVi = "Bản địa đầm lầy châu Mỹ, nay được sưu tầm và nhân giống rộng rãi tại các vườn thực vật Việt Nam.",
            ecologicalRoleEn = "Captures insects to supplement nitrogen and phosphorus lacking in poor soils.",
            ecologicalRoleVi = "Bẫy côn trùng để bổ sung đạm và phốt-pho bù lại đất đầm lầy cằn cỗi.",
            mysteriaFactEn = "The trap counts trigger touches: 1 touch primes it, 2 touches within 20s snap it shut in 0.1 second, and 5 touches start enzyme digestion!",
            mysteriaFactVi = "Bẫy lá có khả năng 'đếm': chạm 1 lần cây chờ đợi, chạm lần 2 trong 20 giây kẹp sẽ đóng sập trong 0.1 giây, và chạm lần 5 sẽ tiết enzyme tiêu hóa con mồi!",
            conservationStatusCode = "VU",
            toxicityOrCareEn = "Never use tap water or fertilizer. Only pure distilled/rainwater and peat moss.",
            toxicityOrCareVi = "Chỉ tưới nước cất hoặc nước mưa tinh khiết, không bón phân hóa học làm cháy rễ.",
            confidenceScore = 97
        ),

        // 10. Golden Bamboo (Tre Vàng / Trúc Đùi Gà)
        SpeciesInfo(
            id = "bambusa_vulgaris",
            commonNameEn = "Golden Bamboo",
            commonNameVi = "Tre Vàng (Tre Mỡ)",
            scientificName = "Bambusa vulgaris",
            category = SpeciesCategory.PLANT.name,
            kingdom = "Plantae",
            family = "Poaceae (Họ Cỏ / Lúa)",
            orderName = "Poales (Bộ Hòa thảo)",
            descriptionEn = "An open-clump type bamboo with golden yellow culms adorned with narrow green stripes.",
            descriptionVi = "Loài tre cảnh quý với thân đốt màu vàng óng ánh điểm xuyết các đường sọc xanh ngọc bích sang trọng.",
            habitatEn = "Riverbanks, tropical roadsides, valleys, and botanical gardens.",
            habitatVi = "Ven bờ sông suối, đồi núi nhiệt đới và cảnh quan sân vườn Á Đông.",
            distributionEn = "Native to Indochina, cultivated pantropically.",
            distributionVi = "Gắn bó ngàn đời với văn hóa làng quê và tinh thần kiên cường của người Việt Nam.",
            ecologicalRoleEn = "Superior soil erosion stabilizer and rapid carbon sequesterer.",
            ecologicalRoleVi = "Hệ rễ đan xen giữ đất chống xói mòn lũ quét và hấp thụ CO2 nhanh gấp 4 lần cây gỗ thường.",
            mysteriaFactEn = "Bamboo is the fastest-growing plant on Earth, capable of growing up to 90 cm (35 inches) in a single day!",
            mysteriaFactVi = "Tre là thực vật phát triển nhanh nhất hành tinh, một số loài có thể cao thêm tới gần 1 mét chỉ trong vòng 24 giờ!",
            conservationStatusCode = "LC",
            toxicityOrCareEn = "Shoots must be boiled before eating to eliminate cyanogenic glycosides.",
            toxicityOrCareVi = "Măng tre non luộc chín thơm ngon, thân tre dùng làm đồ thủ công mỹ nghệ và vật liệu xanh.",
            confidenceScore = 99
        )
    )

    fun getRandomSpecies(): SpeciesInfo {
        return SAMPLE_SPECIES.random().copy(
            id = UUID.randomUUID().toString(),
            identifiedAtMillis = System.currentTimeMillis()
        )
    }

    fun findMatchingSample(keyword: String): SpeciesInfo? {
        val lower = keyword.lowercase()
        return SAMPLE_SPECIES.firstOrNull {
            it.commonNameEn.lowercase().contains(lower) ||
            it.commonNameVi.lowercase().contains(lower) ||
            it.scientificName.lowercase().contains(lower) ||
            it.category.lowercase().contains(lower)
        }
    }
}
