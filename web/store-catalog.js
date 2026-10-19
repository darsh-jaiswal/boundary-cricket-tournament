// Only the four photographed products belong in the current collection.
export const PRODUCT_PHOTOS = Object.freeze({
  'black-full-sleeve': '/assets/products/black-full-sleeve.png',
  'blue-jersey': '/assets/products/blue-jersey.png',
  'white-jersey': '/assets/products/white-jersey.png',
  'maroon-cap': '/assets/products/maroon-cap.png',
});
export const photoFor = id => Object.hasOwn(PRODUCT_PHOTOS, id) ? PRODUCT_PHOTOS[id] : null;
