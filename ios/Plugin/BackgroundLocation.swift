import Foundation

@objc public class BackgroundLocation: NSObject {
    @objc public func echo(_ value: String) -> String {
        print(value)
        return value
    }
}
