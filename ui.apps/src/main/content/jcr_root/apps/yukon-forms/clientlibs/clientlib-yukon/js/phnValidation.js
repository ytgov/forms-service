/**
 * Validates the PHN
 * @name validatePHN YT Personal Health Number Validation
 */

// check if a number is odd or even
function isEven(idx) {
    return idx % 2 === 0;
}

// check if the number has 9 digits
function isStringValidNumber(str) {
    return /^\d{9}$/.test(str);
}

// Step1: Double the even digits
// Store the resulted number in result
function calcStep1(phn) {
    var result = "";

    for (var i = 0; i < phn.length; i++) {
        var c = phn[i];
        // if the last digit is even, then double its value, else keep the original value
        result += (isEven(i + 1)) ? String(Number(c) * 2) : c;
    }

    return result;
}

// Step2: Add up all the numbers from step 1 but treat double digits as two separate digits
// treat double digits as two separate digits - not relevant as 1 + (2 + 1) = 1 + 2 + 1
// Add up all the digits
function calcStep2(step1Result) {
    var total = 0;

    for (var i = 0; i < step1Result.length; i++) {
        total += Number(step1Result[i]);
    }

    return total;
}

// Step3: Divide the result from step2 by 10
// boolean
// if the result is a whole number (no decimals) then the example is valid
function calcStep3(step2Total) {
    return step2Total > 0 && step2Total % 10 === 0;
}

// Main function
// Get and validate input from user
function validatePHN(phn) {
    phn=phn.toString();

    if (isStringValidNumber(phn)) {
        var step1Result = calcStep1(phn);
        var step2Total = calcStep2(step1Result);
        return calcStep3(step2Total);
    }

    return false;
}
