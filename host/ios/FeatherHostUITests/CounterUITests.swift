import XCTest

final class CounterUITests: XCTestCase {
    func testPackagedKotlinCounterRunsWithoutDevelopmentServer() throws {
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.staticTexts["Kotlin counter: 0"].waitForExistence(timeout: 30))
        app.buttons["Increment"].tap()
        XCTAssertTrue(app.staticTexts["Kotlin counter: 1"].waitForExistence(timeout: 10))
        app.terminate()
        app.launch()
        XCTAssertTrue(app.staticTexts["Kotlin counter: 0"].waitForExistence(timeout: 30))
    }
}
