package sqlite

import "strings"

func appendDSNQuery(dsn string, query string) string {
	separator := "?"
	if strings.Contains(dsn, "?") {
		separator = "&"
	}
	return dsn + separator + query
}
