import Foundation

enum HashfelaBackgrounds {
    private static let urls = [
        "https://upload.wikimedia.org/wikipedia/commons/thumb/b/ba/TEL_AZEKA_A.jpg/1280px-TEL_AZEKA_A.jpg",
        "https://upload.wikimedia.org/wikipedia/commons/thumb/7/76/Adullam-France_Park.jpg/1280px-Adullam-France_Park.jpg",
        "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e7/Pristine_wilderness_in_Adullam-France_Park.jpg/1280px-Pristine_wilderness_in_Adullam-France_Park.jpg",
        "https://upload.wikimedia.org/wikipedia/commons/thumb/b/ba/Park_Britannia_DSC_1202_%288411816956%29.jpg/1280px-Park_Britannia_DSC_1202_%288411816956%29.jpg",
        "https://upload.wikimedia.org/wikipedia/commons/thumb/5/54/The_Elah_valley.jpg/1280px-The_Elah_valley.jpg",
        "https://upload.wikimedia.org/wikipedia/commons/thumb/e/ea/Valley_of_Elah.jpg/1280px-Valley_of_Elah.jpg",
        "https://upload.wikimedia.org/wikipedia/commons/thumb/8/85/Israel_Beit_Guvrin_P1050959.JPG/1280px-Israel_Beit_Guvrin_P1050959.JPG",
        "https://upload.wikimedia.org/wikipedia/commons/thumb/c/ca/135175_eshtaol_forest_observation_PikiWiki_Israel.jpg/1280px-135175_eshtaol_forest_observation_PikiWiki_Israel.jpg"
    ]

    static func randomURL() -> URL {
        URL(string: urls.randomElement() ?? urls[0])!
    }
}
