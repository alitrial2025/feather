import Foundation

/// A minimal MessagePack decoder for the Feather iOS Host.
/// Supports: Null, Boolean, Integer, Float, String, Array, Map.
enum MessagePackDecoder {
    static func decode(_ data: Data) -> Any? {
        var offset = 0
        return decode(data, offset: &offset)
    }

    private static func decode(_ data: Data, offset: inout Int) -> Any? {
        guard offset < data.count else { return nil }
        let b = data[offset]
        offset += 1

        switch b {
        case 0xc0: return nil
        case 0xc2: return false
        case 0xc3: return true
        
        // FixInt
        case 0x00...0x7f: return Int(b)
        case 0xe0...0xff: return Int(Int8(bitPattern: b))
        
        // Uint8, 16, 32, 64
        case 0xcc: 
            let v = data[offset]; offset += 1
            return Int(v)
        case 0xcd:
            let v = UInt16(data[offset]) << 8 | UInt16(data[offset+1]); offset += 2
            return Int(v)
        case 0xce:
            let v = UInt32(data[offset]) << 24 | UInt32(data[offset+1]) << 16 | UInt32(data[offset+2]) << 8 | UInt32(data[offset+3]); offset += 4
            return Int(v)
            
        // Float 32/64
        case 0xca:
            let v = data.subdata(in: offset..<offset+4).withUnsafeBytes { $0.load(as: Float32.self).bitPattern.byteSwapped }
            // Correct way to load float from big-endian
            let bits = UInt32(bigEndian: data.subdata(in: offset..<offset+4).withUnsafeBytes { $0.load(as: UInt32.self) })
            offset += 4
            return Float32(bitPattern: bits)
        case 0xcb:
            let bits = UInt64(bigEndian: data.subdata(in: offset..<offset+8).withUnsafeBytes { $0.load(as: UInt64.self) })
            offset += 8
            return Float64(bitPattern: bits)

        // String
        case 0xa0...0xbf:
            let len = Int(b & 0x1f)
            return decodeString(data, len: len, offset: &offset)
        case 0xd9:
            let len = Int(data[offset]); offset += 1
            return decodeString(data, len: len, offset: &offset)
        case 0xda:
            let len = Int(UInt16(bigEndian: data.subdata(in: offset..<offset+2).withUnsafeBytes { $0.load(as: UInt16.self) }))
            offset += 2
            return decodeString(data, len: len, offset: &offset)
        case 0xdb:
            let len = Int(UInt32(bigEndian: data.subdata(in: offset..<offset+4).withUnsafeBytes { $0.load(as: UInt32.self) }))
            offset += 4
            return decodeString(data, len: len, offset: &offset)

        // Array
        case 0x90...0x9f:
            let len = Int(b & 0x0f)
            return decodeArray(data, len: len, offset: &offset)
        case 0xdc:
            let len = Int(UInt16(bigEndian: data.subdata(in: offset..<offset+2).withUnsafeBytes { $0.load(as: UInt16.self) }))
            offset += 2
            return decodeArray(data, len: len, offset: &offset)
        case 0xdd:
            let len = Int(UInt32(bigEndian: data.subdata(in: offset..<offset+4).withUnsafeBytes { $0.load(as: UInt32.self) }))
            offset += 4
            return decodeArray(data, len: len, offset: &offset)

        // Map
        case 0x80...0x8f:
            let len = Int(b & 0x0f)
            return decodeMap(data, len: len, offset: &offset)
        case 0xde:
            let len = Int(UInt16(bigEndian: data.subdata(in: offset..<offset+2).withUnsafeBytes { $0.load(as: UInt16.self) }))
            offset += 2
            return decodeMap(data, len: len, offset: &offset)
        case 0xdf:
            let len = Int(UInt32(bigEndian: data.subdata(in: offset..<offset+4).withUnsafeBytes { $0.load(as: UInt32.self) }))
            offset += 4
            return decodeMap(data, len: len, offset: &offset)

        default: return nil
        }
    }

    private static func decodeString(_ data: Data, len: Int, offset: inout Int) -> String? {
        guard offset + len <= data.count else { return nil }
        let s = String(data: data.subdata(in: offset..<offset+len), encoding: .utf8)
        offset += len
        return s
    }

    private static func decodeArray(_ data: Data, len: Int, offset: inout Int) -> [Any?] {
        var arr = [Any?]()
        for _ in 0..<len {
            arr.append(decode(data, offset: &offset))
        }
        return arr
    }

    private static func decodeMap(_ data: Data, len: Int, offset: inout Int) -> [String: Any?] {
        var dict = [String: Any?]()
        for _ in 0..<len {
            if let key = decode(data, offset: &offset) as? String {
                dict[key] = decode(data, offset: &offset)
            } else {
                _ = decode(data, offset: &offset)
            }
        }
        return dict
    }
}
