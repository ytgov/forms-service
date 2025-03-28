/**
 * Generate unique ID
 * @name generateUniqueId Generate unique ID
 * @returns {string}
 */

// Returns the ID as a String value
// Uses a combination of Simple Timestamp and a Random string of 7 characters
function generateUniqueId(){
    var id = "";

    // Get current Date time as milliseconds since Jan 1, 1970
    const timestamp = Date.now();

    // Generate a short, pseudo-random alphanumeric string using base36 random part
    const random = Math.random().toString(36).substr(2, 7);

    id = timestamp + "-" + random;
    return id;
}