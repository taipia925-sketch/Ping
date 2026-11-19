package main

import (
	"fmt"
	"strings"
	"time"
)

// Formati all'italiana: virgola decimale, precisione adeguata al valore.
func fmtMs(v float64) string {
	var s string
	switch {
	case v < 1:
		s = fmt.Sprintf("%.2f", v)
	case v < 10:
		s = fmt.Sprintf("%.1f", v)
	default:
		s = fmt.Sprintf("%.0f", v)
	}
	return strings.Replace(s, ".", ",", 1)
}

func fmtPct(v float64) string {
	if v == 0 || v == 100 {
		return fmt.Sprintf("%.0f%%", v)
	}
	return strings.Replace(fmt.Sprintf("%.1f%%", v), ".", ",", 1)
}

func fmtTime(ms int64) string     { return time.UnixMilli(ms).Format("15:04:05") }
func fmtDateTime(ms int64) string { return time.UnixMilli(ms).Format("02/01/2006 15:04:05") }

func fmtDuration(ms int64) string {
	s := ms / 1000
	h, m, sec := s/3600, (s%3600)/60, s%60
	switch {
	case h > 0:
		return fmt.Sprintf("%d h %d min", h, m)
	case m > 0:
		return fmt.Sprintf("%d min %d s", m, sec)
	}
	return fmt.Sprintf("%d s", sec)
}
