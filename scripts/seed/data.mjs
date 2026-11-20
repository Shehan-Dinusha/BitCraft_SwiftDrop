// Reference data for the demo seeder: Sri Lanka's 25 districts with real towns and
// postal codes, plus name pools. All people are fictional; e-mail addresses use the
// reserved example.com domain, so nothing can ever reach a real inbox.

// mix = share of [Sinhala, Tamil, Muslim] names used for people in that district.
// weight = rough population weight, used to spread customers and destinations.
export const DISTRICTS = [
  { code: 'COLOMBO', weight: 10, mix: [0.7, 0.1, 0.2],
    towns: [['Colombo 03', '00300'], ['Colombo 05', '00500'], ['Dehiwala', '10350'], ['Nugegoda', '10250'], ['Maharagama', '10280']] },
  { code: 'GAMPAHA', weight: 8, mix: [0.85, 0.05, 0.1],
    towns: [['Gampaha', '11000'], ['Negombo', '11500'], ['Kadawatha', '11850'], ['Ja-Ela', '11350']] },
  { code: 'KALUTARA', weight: 5, mix: [0.85, 0.03, 0.12],
    towns: [['Kalutara', '12000'], ['Panadura', '12500'], ['Horana', '12400']] },
  { code: 'KANDY', weight: 6, mix: [0.75, 0.1, 0.15],
    towns: [['Kandy', '20000'], ['Peradeniya', '20400'], ['Katugastota', '20800']] },
  { code: 'MATALE', weight: 2, mix: [0.8, 0.1, 0.1],
    towns: [['Matale', '21000'], ['Dambulla', '21100']] },
  { code: 'NUWARA_ELIYA', weight: 2, mix: [0.55, 0.4, 0.05],
    towns: [['Nuwara Eliya', '22200'], ['Hatton', '22000']] },
  { code: 'GALLE', weight: 4, mix: [0.9, 0.02, 0.08],
    towns: [['Galle', '80000'], ['Hikkaduwa', '80240'], ['Ambalangoda', '80300']] },
  { code: 'MATARA', weight: 3, mix: [0.9, 0.02, 0.08],
    towns: [['Matara', '81000'], ['Weligama', '81700']] },
  { code: 'HAMBANTOTA', weight: 2, mix: [0.92, 0.02, 0.06],
    towns: [['Hambantota', '82000'], ['Tangalle', '82200']] },
  { code: 'JAFFNA', weight: 2, mix: [0.03, 0.95, 0.02],
    towns: [['Jaffna', '40000'], ['Chavakachcheri', '40050']] },
  { code: 'KILINOCHCHI', weight: 1, mix: [0.03, 0.95, 0.02],
    towns: [['Kilinochchi', '44000']] },
  { code: 'MANNAR', weight: 1, mix: [0.1, 0.6, 0.3],
    towns: [['Mannar', '41000']] },
  { code: 'VAVUNIYA', weight: 1, mix: [0.3, 0.6, 0.1],
    towns: [['Vavuniya', '43000']] },
  { code: 'MULLAITIVU', weight: 1, mix: [0.05, 0.9, 0.05],
    towns: [['Mullaitivu', '42000']] },
  { code: 'BATTICALOA', weight: 2, mix: [0.05, 0.7, 0.25],
    towns: [['Batticaloa', '30000'], ['Kattankudy', '30100']] },
  { code: 'AMPARA', weight: 2, mix: [0.4, 0.2, 0.4],
    towns: [['Ampara', '32000'], ['Kalmunai', '32300']] },
  { code: 'TRINCOMALEE', weight: 2, mix: [0.35, 0.35, 0.3],
    towns: [['Trincomalee', '31000'], ['Kinniya', '31100']] },
  { code: 'KURUNEGALA', weight: 5, mix: [0.9, 0.02, 0.08],
    towns: [['Kurunegala', '60000'], ['Kuliyapitiya', '60200']] },
  { code: 'PUTTALAM', weight: 2, mix: [0.7, 0.1, 0.2],
    towns: [['Puttalam', '61300'], ['Chilaw', '61000']] },
  { code: 'ANURADHAPURA', weight: 3, mix: [0.9, 0.02, 0.08],
    towns: [['Anuradhapura', '50000'], ['Kekirawa', '50100']] },
  { code: 'POLONNARUWA', weight: 2, mix: [0.9, 0.03, 0.07],
    towns: [['Polonnaruwa', '51000']] },
  { code: 'BADULLA', weight: 3, mix: [0.75, 0.2, 0.05],
    towns: [['Badulla', '90000'], ['Bandarawela', '90100']] },
  { code: 'MONARAGALA', weight: 2, mix: [0.95, 0.02, 0.03],
    towns: [['Monaragala', '91000'], ['Wellawaya', '91200']] },
  { code: 'RATNAPURA', weight: 4, mix: [0.85, 0.12, 0.03],
    towns: [['Ratnapura', '70000'], ['Balangoda', '70100'], ['Embilipitiya', '70200']] },
  { code: 'KEGALLE', weight: 3, mix: [0.85, 0.07, 0.08],
    towns: [['Kegalle', '71000'], ['Mawanella', '71500']] },
];

export const STREETS = [
  'Main Street', 'Temple Road', 'Station Road', 'Hospital Road', 'School Lane', 'Church Road',
  'Lake Road', 'Market Street', 'Kandy Road', 'Galle Road', 'Park Avenue', 'Flower Road',
  'Old Road', 'New Town Road', 'Mosque Road', 'Kovil Road', 'Bodhi Mawatha', 'Circular Road',
];

// Surnames of SwiftDrop team members are deliberately left out.
export const NAMES = {
  sinhala: {
    male: ['Nimal', 'Kamal', 'Sunil', 'Ruwan', 'Chaminda', 'Tharindu', 'Dilshan', 'Nuwan', 'Lahiru',
      'Chathura', 'Sanjeewa', 'Pradeep', 'Asanka', 'Isuru', 'Malith', 'Gayan', 'Supun', 'Ravindu'],
    female: ['Nadeesha', 'Dilini', 'Sanduni', 'Ishara', 'Hiruni', 'Kavindi', 'Tharushi', 'Madushani',
      'Anjali', 'Piumi', 'Sachini', 'Chamodi', 'Nethmi', 'Rashmi', 'Imesha', 'Yasodha'],
    last: ['Perera', 'Fernando', 'Silva', 'Jayasinghe', 'Wickramasinghe', 'Herath', 'Dissanayake',
      'Gunawardena', 'Senanayake', 'Ranasinghe', 'Karunaratne', 'Rathnayake', 'Samarasinghe',
      'Abeysekara', 'Pathirana', 'Liyanage', 'Ekanayake', 'Wijesinghe', 'Amarasinghe', 'Jayawardena'],
  },
  tamil: {
    male: ['Arun', 'Karthik', 'Suresh', 'Prakash', 'Vithusan', 'Thushanth', 'Mathan', 'Kajan',
      'Niroshan', 'Sajeevan', 'Pirashanth', 'Ramesh'],
    female: ['Priya', 'Kavitha', 'Thanuja', 'Nirosha', 'Sharmila', 'Vithya', 'Janani', 'Keerthana',
      'Mathumitha', 'Abirami'],
    last: ['Sivakumar', 'Rajendran', 'Thevarajah', 'Kandiah', 'Selvarajah', 'Navaratnam',
      'Balasingham', 'Yogarajah', 'Ganeshan', 'Murugesu', 'Ponnambalam', 'Arumugam'],
  },
  muslim: {
    male: ['Rizwan', 'Fazil', 'Imran', 'Ashraf', 'Nazeer', 'Rifkhan', 'Hisham', 'Azam', 'Shafeek', 'Inshaf'],
    female: ['Fathima', 'Aysha', 'Zainab', 'Shifana', 'Rifka', 'Nusra', 'Hafsa', 'Sanoofa', 'Rumaiza'],
    last: ['Mohamed', 'Ismail', 'Hameed', 'Nazeer', 'Farook', 'Cassim', 'Razik', 'Marikar', 'Haniffa', 'Azeez'],
  },
};

export const ITEMS = [
  'Books and stationery', 'Clothing', 'Mobile phone accessories', 'Handloom saree', 'Ceylon tea packets',
  'Spices and condiments', 'Office documents', 'Shoes', 'Kitchenware', 'Laptop bag', 'Children\'s toys',
  'Cosmetics', 'Ayurvedic products', 'Batik shirts', 'Electronic spare parts', 'Wedding invitation cards',
  'Handicrafts', 'Cricket bat', 'Bed sheets', 'Dried fish and sweets for family',
];

export const NOTES = [
  'Call when you reach the gate', 'Leave with the security guard', 'Near the temple junction',
  'Second floor, please ring the bell', 'Opposite the bus stand', 'Behind the pharmacy',
  'Please deliver after 4 pm', 'Blue gate, house with mango tree', 'Fragile, handle with care',
];

export const CANCEL_REASONS = [
  'Ordered by mistake', 'Recipient will collect it in person', 'Changed delivery address',
  'Found a cheaper option', 'No longer needed',
];

// Valid DeliveryFailureReason codes, grouped by the leg on which they make sense.
export const FAILURES = {
  OUT_FOR_DELIVERY: ['CUSTOMER_UNAVAILABLE', 'RECIPIENT_UNAVAILABLE', 'WRONG_ADDRESS',
    'REFUSED_BY_CUSTOMER', 'ACCESS_RESTRICTED'],
  IN_TRANSIT: ['ADVERSE_WEATHER'],
  PICKED_UP: ['DAMAGED_IN_TRANSIT'],
};

export const VEHICLES = [['MOTORCYCLE', 60], ['THREE_WHEELER', 20], ['VAN', 15], ['BICYCLE', 5]];

// Fictional businesses (names invented for the demo). outcome decides what the admin does
// with the application: APPROVED merchants get an API key and send B2B orders; one stays
// PENDING so the approval screen has work; one is REJECTED with a reason.
export const MERCHANTS = [
  { slug: 'kandy-valley-spice', business: 'Kandy Valley Spice Co.', district: 'KANDY', outcome: 'APPROVED', orders: 12,
    items: ['Ceylon cinnamon quills', 'Black pepper pack', 'Cardamom pods', 'Spice gift box', 'Curry powder set'] },
  { slug: 'wayamba-handloom', business: 'Wayamba Handloom Studio', district: 'KURUNEGALA', outcome: 'APPROVED', orders: 10,
    items: ['Handloom saree', 'Handloom shirt', 'Cushion covers', 'Table runner'] },
  { slug: 'southern-lantern-crafts', business: 'Southern Lantern Crafts', district: 'GALLE', outcome: 'APPROVED', orders: 10,
    items: ['Wooden mask', 'Lacquerware bowl', 'Batik wall hanging', 'Brass oil lamp'] },
  { slug: 'nallur-palmyrah-works', business: 'Nallur Palmyrah Works', district: 'JAFFNA', outcome: 'APPROVED', orders: 8,
    items: ['Palmyrah basket', 'Palmyrah jaggery', 'Woven mat', 'Palmyrah fan'] },
  { slug: 'pettah-gadget-hub', business: 'Pettah Gadget Hub', district: 'COLOMBO', outcome: 'PENDING', orders: 0, items: [] },
  { slug: 'lagoon-catch-exports', business: 'Lagoon Catch Exports', district: 'GAMPAHA', outcome: 'REJECTED', orders: 0, items: [],
    reason: 'Fresh and frozen seafood is perishable and is not accepted under the SwiftDrop parcel policy.' },
];
