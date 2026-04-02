package sqlite

import "golang.org/x/text/cases"

// unicodeFold provides Unicode case folding for case-insensitive comparisons.
// It's safe to use concurrently and reused across all function calls.
var unicodeFold = cases.Fold()

func foldUnicodeValue(value any) any {
	switch v := value.(type) {
	case nil:
		return nil
	case string:
		return unicodeFold.String(v)
	case []byte:
		return unicodeFold.String(string(v))
	default:
		return v
	}
}
