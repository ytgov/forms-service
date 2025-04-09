/**
 * Validate Date based on a start date and a nr of days set by the Author. End Date needs to be later than the start date plus the nr of set days
 * @name validateDateByDaysPassed Validate Date based on Days Passed
 * @params {Date} startDate in Date format, {Number} nrOfDaysPassed in Numeric format, {Date} endDate in Date format
 * @returns {boolean}
 */

// Returns true if start Date is later than the Nr of Days Passed
// Returns false if start Date is earlier than the Nr of Days Passed
function validateDateByDaysPassed(startDate, nrOfDaysPassed, endDate){
    // Convert inputs to Date objects if they're not already
    var start = new Date(startDate);
    var end = new Date(endDate);

    // Calculate the difference in time - result is in milliseconds
    const timeDifference = end.getTime() - start.getTime();

    // Convert milliseconds to days - Current Difference in Days between Dates
    const currentDiffDaysBetweenDates = timeDifference / (1000 * 60 * 60 * 24);

    // Return true if difference is greater than or equal to requiredDaysLater and false otherwise
    return currentDiffDaysBetweenDates >= nrOfDaysPassed;
}